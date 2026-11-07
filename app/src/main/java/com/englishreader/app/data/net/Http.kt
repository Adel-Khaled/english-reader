package com.englishreader.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : IOException(message)

/** Minimal HTTP client (no extra dependencies) that follows cross-host redirects. */
object Http {
    private const val UA = "EnglishReader/1.0 (Android; educational reading app)"

    /** POST with a body; returns the response text. Errors include the server's message. */
    suspend fun post(url: String, body: String, contentType: String, timeoutMs: Int = 30_000): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Content-Type", contentType)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw HttpException(code, "HTTP $code ${text.take(300)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun postForm(url: String, params: Map<String, String>, timeoutMs: Int = 30_000): String =
        post(url, params.entries.joinToString("&") { (k, v) -> java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8") },
            "application/x-www-form-urlencoded; charset=UTF-8", timeoutMs)

    private fun open(url: String, timeoutMs: Int): HttpURLConnection {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw HttpException(code, "Redirect without location")
                current = URL(URL(current), loc).toString()
                conn.disconnect()
            } else if (code !in 200..299) {
                conn.disconnect()
                throw HttpException(code, "HTTP $code")
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    suspend fun getString(url: String, timeoutMs: Int = 15_000): String = withContext(Dispatchers.IO) {
        val conn = open(url, timeoutMs)
        try {
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(url: String, target: File, timeoutMs: Int = 60_000) = withContext(Dispatchers.IO) {
        val conn = open(url, timeoutMs)
        val tmp = File(target.parentFile, target.name + ".part")
        try {
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true); tmp.delete()
            }
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }
}
