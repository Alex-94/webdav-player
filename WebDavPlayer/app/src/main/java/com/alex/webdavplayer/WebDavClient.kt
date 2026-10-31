package com.alex.webdavplayer

import android.util.Xml
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

object WebDavClient {

    data class Entry(
        val name: String,
        val rawHref: String,
        val isDir: Boolean,
        val size: Long
    )

    fun list(url: String, user: String, pass: String): List<Entry> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "PROPFIND"
        conn.connectTimeout = 8000
        conn.readTimeout = 20000
        conn.setRequestProperty("Depth", "1")
        if (user.isNotBlank()) {
            val token = Base64.getEncoder()
                .encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $token")
        }
        val code = conn.responseCode
        if (code != 207 && code != 200) {
            throw RuntimeException("HTTP $code")
        }
        val body = conn.inputStream.readBytes().toString(Charsets.UTF_8)
        return parse(body)
    }

    private fun parse(xml: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val p = Xml.newPullParser()
        p.setInput(xml.reader())
        var href: String? = null
        var display: String? = null
        var isDir = false
        var size = 0L
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName(p.name)) {
                        "response" -> { href = null; display = null; isDir = false; size = 0L }
                        "href" -> href = p.nextText()
                        "displayname" -> display = p.nextText()
                        "collection" -> isDir = true
                        "getcontentlength" -> size = p.nextText().toLongOrNull() ?: 0L
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localName(p.name) == "response") {
                        val h = href
                        if (!h.isNullOrEmpty() && h != "/") {
                            val dec = URLDecoder.decode(h.trimEnd('/'), "UTF-8")
                            val seg = dec.substringAfterLast('/')
                            val name = display?.takeIf { it.isNotBlank() } ?: seg
                            out.add(Entry(name, h, isDir, size))
                        }
                    }
                }
            }
            event = p.next()
        }
        return out
    }

    private fun localName(raw: String): String =
        raw.substringAfter(':').lowercase()
}
