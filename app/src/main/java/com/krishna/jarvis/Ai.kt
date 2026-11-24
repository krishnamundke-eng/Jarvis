package com.krishna.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Ai {

    const val MODEL = "gemini-3.5-flash-lite"

    private const val RULES =
        "You are JARVIS, a friendly voice assistant on an Android phone. " +
        "Reply in the same language the user used (Hindi, Hinglish or English). " +
        "If the user wrote Hinglish in roman letters, reply in Hindi using Devanagari. " +
        "Keep replies short: 1 to 3 sentences, plain text only, no markdown, no emojis."

    fun ask(key: String, question: String, onResult: (String) -> Unit) {
        Thread {
            val reply = try {
                call(key, question)
            } catch (e: Exception) {
                "इंटरनेट में दिक्कत है, कनेक्शन जाँच लीजिए"
            }
            onResult(reply)
        }.start()
    }

    private fun call(key: String, question: String): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        )
        val con = url.openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 15000
        con.readTimeout = 30000
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("x-goog-api-key", key)

        val sys = JSONObject().put(
            "parts", JSONArray().put(JSONObject().put("text", RULES))
        )
        val user = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", question)))
        val body = JSONObject()
            .put("systemInstruction", sys)
            .put("contents", JSONArray().put(user))

        con.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = con.responseCode
        if (code != 200) {
            return when (code) {
                400, 403 -> "API key सही नहीं है, सेटिंग्स में दोबारा डालिए"
                404 -> "AI मॉडल नहीं मिला, मॉडल का नाम बदलना पड़ेगा"
                429 -> "अभी बहुत सवाल हो गए, थोड़ी देर बाद पूछिए"
                else -> "AI से जवाब नहीं आया, कोड $code"
            }
        }

        val text = con.inputStream.bufferedReader().readText()
        val parts = JSONObject(text)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        val out = sb.toString().trim()
        return if (out.isEmpty()) "मुझे जवाब नहीं मिला" else out
    }
}
