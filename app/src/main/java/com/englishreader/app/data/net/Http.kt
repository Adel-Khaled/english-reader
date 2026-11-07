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
