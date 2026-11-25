package com.krishna.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Ai {

    const val MODEL = "gemini-3.5-flash-lite"

    fun ask(key: String, prompt: String, hindi: Boolean, onResult: (String) -> Unit) {
        Thread {
            val reply = try {
                call(key, prompt, hindi)
            } catch (e: Exception) {
                if (hindi) "इंटरनेट में दिक्कत है, कनेक्शन जाँच लीजिए"
                else "Internet problem. Please check your connection."
            }
            onResult(reply)
        }.start()
    }

    private fun call(key: String, prompt: String, hindi: Boolean): String {
        val rules =
            "You are JARVIS, a friendly and smart assistant on an Android phone, " +
            "like the assistant in Iron Man. " +
            (if (hindi) "Always reply in Hindi (Devanagari script). "
            else "Always reply in English. ") +
            "Be clear and concise: usually 1 to 4 sentences unless the user asks for detail. " +
            "Plain text only, no markdown, no asterisks, no emojis."

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
            "parts", JSONArray().put(JSONObject().put("text", rules))
        )
        val user = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        val body = JSONObject()
            .put("systemInstruction", sys)
            .put("contents", JSONArray().put(user))

        con.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = con.responseCode
        if (code != 200) {
            return when (code) {
                400, 403 ->
                    if (hindi) "API key सही नहीं है, सेटिंग्स में दोबारा डालिए"
                    else "The API key looks wrong. Please enter it again in Settings."
                404 ->
                    if (hindi) "AI मॉडल नहीं मिला, मॉडल का नाम बदलना पड़ेगा"
                    else "The AI model was not found. The model name needs to be changed."
                429 ->
                    if (hindi) "अभी बहुत सवाल हो गए, थोड़ी देर बाद पूछिए"
                    else "Too many questions right now. Please try again in a minute."
                else ->
                    if (hindi) "AI से जवाब नहीं आया, कोड $code"
                    else "No answer from the AI. Code $code"
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
        return if (out.isEmpty()) {
            if (hindi) "मुझे जवाब नहीं मिला" else "I couldn't get an answer."
        } else out
    }
}
