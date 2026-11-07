package com.englishreader.app.data.net

import org.json.JSONObject
import java.net.URLEncoder

/** MyMemory free translation API (no key needed for light use). */
object OnlineTranslateApi {
    suspend fun translateEnToAr(text: String): String? {
        val q = URLEncoder.encode(text.take(450), "UTF-8")
        val json = JSONObject(Http.getString("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Car", 12_000))
        if (json.optInt("responseStatus", 200) != 200) return null
        val translated = json.optJSONObject("responseData")?.optString("translatedText")?.trim().orEmpty()
        if (translated.isEmpty()) return null
        // MyMemory returns warnings in English in the same field when quota is exceeded.
        if (translated.contains("MYMEMORY WARNING", ignoreCase = true)) return null
        if (translated.equals(text, ignoreCase = true)) return null
        // Must contain at least one Arabic letter to be a useful result.
        if (translated.none { it in '؀'..'ۿ' }) return null
        return translated
    }
}
