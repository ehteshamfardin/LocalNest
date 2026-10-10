package com.example.server

import android.content.Context

object LocalShareWebHtml {

    @Volatile
    private var cachedHtml: String? = null

    fun getIndexHtml(context: Context? = null): String {
        cachedHtml?.let { return it }

        if (context != null) {
            try {
                context.assets.open("web/index.html").bufferedReader().use { reader ->
                    val text = reader.readText()
                    cachedHtml = text
                    return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return fallbackHtml
    }

    private val fallbackHtml: String = """<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>LocalShare</title></head>
<body style="font-family:sans-serif;background:#0d1117;color:#fff;padding:40px;text-align:center;">
  <h2>LocalShare Personal Network</h2>
  <p>Connecting to active shares...</p>
</body>
</html>"""
}
