package com.alex.webdavplayer

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object WebDavClient {

    data class Entry(
        val name: String,
        val rawHref: String,
        val isDir: Boolean,
        val size: Long
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val PROPFIND_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:displayname/><d:resourcetype/><d:getcontentlength/>" +
            "</d:prop></d:propfind>"

    fun list(url: String, user: String, pass: String): List<Entry> {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Depth", "1")
        if (user.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(user, pass))
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code != 207 && resp.code != 200) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val text = resp.body?.string() ?: ""
            return parse(text)
        }
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
