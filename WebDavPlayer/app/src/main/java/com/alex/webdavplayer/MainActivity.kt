package com.alex.webdavplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.net.URLDecoder
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        const val PREFS = "config"
        const val KEY_FAVS = "favs"
        val VIDEO_EXT = setOf(
            "mp4", "mkv", "avi", "ts", "m2ts", "mts", "mov", "flv", "wmv",
            "webm", "rmvb", "rm", "mpg", "mpeg", "vob", "3gp", "m4v", "tp", "trp"
        )
        const val DEFAULT_SERVER = "http://192.168.31.61:8296"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var listView: ListView
    private lateinit var statusView: TextView
    private lateinit var pathView: TextView
    private var currentPath = "/"
    private var favMode = false
    private val entries = mutableListOf<WebDavClient.Entry>()
    private lateinit var adapter: MyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.list)
        statusView = findViewById(R.id.status)
        pathView = findViewById(R.id.path)
        findViewById<Button>(R.id.btnHome).setOnClickListener { load("/") }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnFav).setOnClickListener { showFavs() }

        adapter = MyAdapter()
        listView.adapter = adapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, pos, _ -> onItem(entries[pos]) }
        listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, pos, _ ->
                if (pos in entries.indices) {
                    toggleFav(entries[pos])
                }
                true
            }

        currentPath = prefs.getString("lastPath", "/") ?: "/"
        load(currentPath)
    }

    private fun serverBase(): String =
        (prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER).trim().trimEnd('/')

    // ---------- 收藏存取（本地 SharedPreferences） ----------

    private fun favSet(): MutableSet<String> =
        LinkedHashSet(prefs.getStringSet(KEY_FAVS, emptySet()) ?: emptySet())

    private fun recordOf(e: WebDavClient.Entry): String =
        "${e.rawHref}\t${e.name}\t${e.lastMod}\t${e.size}"

    private fun isFav(rawHref: String): Boolean =
        favSet().any { it.substringBefore('\t') == rawHref }

    private fun toggleFav(e: WebDavClient.Entry) {
        val set = favSet()
        val existed = set.removeAll { it.substringBefore('\t') == e.rawHref }
        if (!existed) set.add(recordOf(e))
        prefs.edit().putStringSet(KEY_FAVS, set).apply()
        Toast.makeText(
            this,
            if (existed) "已取消收藏" else "★ 已收藏",
            Toast.LENGTH_SHORT
        ).show()
        if (favMode) showFavs() else adapter.notifyDataSetChanged()
    }

    private fun showFavs() {
        favMode = true
        val favs = favSet().mapNotNull { rec ->
            val parts = rec.split('\t')
            if (parts.size < 4) return@mapNotNull null
            WebDavClient.Entry(
                name = parts[1],
                rawHref = parts[0],
                isDir = false,
                size = parts[2].toLongOrNull() ?: 0L,
                lastMod = parts[3].toLongOrNull() ?: 0L
            )
        }.sortedByDescending { it.lastMod }

        entries.clear()
        entries.addAll(favs)
        adapter.notifyDataSetChanged()
        pathView.text = "★ 收藏列表 (${favs.size})"
        listView.setSelection(0)
        statusView.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        statusView.text = if (favs.isEmpty()) "暂无收藏：在列表中长按即可收藏" else ""
    }

    // ---------- 目录浏览 ----------

    private fun onItem(e: WebDavClient.Entry) {
        if (e.isDir) {
            if (favMode) favMode = false
            load(e.rawHref)
        } else {
            play(e)
        }
    }

    private fun play(e: WebDavClient.Entry) {
        val url = withAuth(serverBase() + e.rawHref)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(url), "video/*")
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(this, "未找到可用的视频播放器", Toast.LENGTH_LONG).show()
        }
    }

    private fun withAuth(url: String): String {
        val user = prefs.getString("user", "") ?: ""
        val pass = prefs.getString("pass", "") ?: ""
        if (user.isBlank()) return url
        return url.replace("://", "://${Uri.encode(user)}:${Uri.encode(pass)}@")
    }

    private fun load(path: String) {
        favMode = false
        statusView.visibility = View.VISIBLE
        statusView.text = "加载中..."
        entries.clear()
        adapter.notifyDataSetChanged()
        thread {
            try {
                val all = WebDavClient.list(
                    serverBase() + path,
                    prefs.getString("user", "") ?: "",
                    prefs.getString("pass", "") ?: ""
                ).filter { it.rawHref != path }

                // 默认排序：文件夹在前、视频在后，各组内按修改时间倒序（新的在前）
                val dirs = all.filter { it.isDir }.sortedByDescending { it.lastMod }
                val vids = all.filter {
                    !it.isDir && it.name.substringAfterLast('.', "").lowercase() in VIDEO_EXT
                }.sortedByDescending { it.lastMod }
                val result = dirs + vids

                runOnUiThread {
                    currentPath = path
                    prefs.edit().putString("lastPath", path).apply()
                    pathView.text = "目录: " + decodePath(path)
                    entries.addAll(result)
                    adapter.notifyDataSetChanged()
                    listView.setSelection(0)
                    statusView.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                    statusView.text = when {
                        dirs.isEmpty() && vids.isEmpty() -> "目录为空"
                        vids.isEmpty() -> "此目录下没有视频文件"
                        else -> ""
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.visibility = View.VISIBLE
                    statusView.text = "加载失败: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun decodePath(path: String): String =
        try {
            URLDecoder.decode(path.trimEnd('/').ifEmpty { "/" }, "UTF-8")
        } catch (e: Exception) {
            path
        }

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etServer = view.findViewById<EditText>(R.id.etServer)
        val etPath = view.findViewById<EditText>(R.id.etPath)
        val etUser = view.findViewById<EditText>(R.id.etUser)
        val etPass = view.findViewById<EditText>(R.id.etPass)
        etServer.setText(prefs.getString("server", DEFAULT_SERVER))
        etPath.setText(if (favMode) "" else decodePath(currentPath))
        etUser.setText(prefs.getString("user", ""))
        etPass.setText(prefs.getString("pass", ""))

        AlertDialog.Builder(this)
            .setTitle("服务器设置")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val server = etServer.text.toString().trim().trimEnd('/')
                var start = etPath.text.toString().trim()
                if (start.isNotEmpty() && !start.startsWith("/")) start = "/$start"
                prefs.edit()
                    .putString("server", server.ifEmpty { DEFAULT_SERVER })
                    .putString("user", etUser.text.toString().trim())
                    .putString("pass", etPass.text.toString())
                    .apply()
                load(if (start.isEmpty()) "/" else start.trimEnd('/') + "/")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onBackPressed() {
        if (favMode) {
            load(currentPath)
            return
        }
        val p = currentPath.trimEnd('/')
        if (p.isEmpty()) {
            super.onBackPressed()
        } else {
            val idx = p.lastIndexOf('/')
            load(if (idx <= 0) "/" else p.substring(0, idx + 1))
        }
    }

    private inner class MyAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_video, parent, false)
            val tv = v.findViewById<TextView>(R.id.text)
            val e = entries[position]
            val star = if (isFav(e.rawHref)) "★ " else ""
            if (e.isDir) {
                tv.text = "$star▸ ${e.name}/"
                tv.setTextColor(0xFF8AB4F8.toInt())
            } else {
                tv.text = "$star${e.name}   ${formatSize(e.size)}"
                tv.setTextColor(0xFFE8E8E8.toInt())
            }
            return v
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
        bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
        else -> "${bytes / 1024} KB"
    }
}
