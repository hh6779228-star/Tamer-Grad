package com.tamergrad.music

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

class MainActivity : Activity() {

    private data class Song(val title: String, val uri: Uri)

    private val songs = mutableListOf<Song>()
    private val favorites = mutableSetOf<String>()
    private val selectedSongs = mutableSetOf<String>()
    private lateinit var selectionStatus: TextView
    private lateinit var selectAllButton: Button

    private var controller: MediaController? = null
    private lateinit var controllerFuture:
        com.google.common.util.concurrent.ListenableFuture<MediaController>

    private lateinit var root: LinearLayout
    private lateinit var songList: LinearLayout
    private lateinit var status: TextView
    private lateinit var playButton: Button

    private val blue = Color.rgb(35, 145, 255)
    private val background = Color.rgb(8, 12, 22)
    private val cardColor = Color.rgb(19, 28, 44)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = background
        window.navigationBarColor = background

        favorites.addAll(
            getSharedPreferences("tamer_pro", Context.MODE_PRIVATE)
                .getStringSet("favorites", emptySet()) ?: emptySet()
        )

        buildInterface()
        connectMusicService()
        checkPermission()
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 16)
            setBackground(background)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "TAMER GRAD PRO"
            textSize = 28f
            setTextColor(blue)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 5)
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "YOUR MUSIC. YOUR WORLD."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        content.addView(subtitle)

        status = TextView(this).apply {
            text = "جاري تجهيز مشغل الموسيقى..."
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(8, 25, 8, 20)
        }
        content.addView(status)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        val previous = makeButton("⏮")
        previous.setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        playButton = makeButton("▶")
        playButton.setOnClickListener {
            val p = controller ?: return@setOnClickListener
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.mediaItemCount == 0 && songs.isNotEmpty()) {
                    playSong(0)
                } else {
                    p.play()
                }
            }
            updatePlayback()
        }

        val next = makeButton("⏭")
        next.setOnClickListener {
            controller?.seekToNextMediaItem()
        }

        controls.addView(previous)
        controls.addView(playButton)
        controls.addView(next)
        content.addView(controls)

        val heading = TextView(this).apply {
            text = "مكتبة الموسيقى"
            textSize = 20f
            setTextColor(blue)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 25, 0, 12)
        }
        content.addView(heading)

        val selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        selectionStatus = TextView(this).apply {
            text = "المحدد: 0"
            textSize = 14f
            setTextColor(white)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        selectAllButton = makeButton("تحديد الكل").apply {
            textSize = 12f
            setOnClickListener {
                if (songs.isNotEmpty() && selectedSongs.size == songs.size) {
                    selectedSongs.clear()
                } else {
                    selectedSongs.clear()
                    selectedSongs.addAll(songs.map { it.uri.toString() })
                }
                loadSongs()
            }
        }

        selectionBar.addView(selectionStatus)
        selectionBar.addView(selectAllButton)
        content.addView(selectionBar)

        val sendButton = makeButton("إرسال المحدد إلى Telegram").apply {
            textSize = 14f
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    if (selectedSongs.isEmpty())
                        "حدد أغنية أولاً"
                    else
                        "تم تحديد ${selectedSongs.size} ملف. سيتم ربط الإرسال لاحقاً",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        content.addView(sendButton)

        songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(songList)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "TAMER GRAD PRO  •  VERSION 2.0"
            textSize = 10f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 5)
        }
        root.addView(footer)

        setContentView(root)
    }

    private fun makeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 19f
            setTextColor(white)
            backgroundTintList = ColorStateList.valueOf(cardColor)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                0, 58, 1f
            ).apply {
                setMargins(5, 8, 5, 8)
            }
        }
    }

    private fun connectMusicService() {
        val token = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        controllerFuture =
            MediaController.Builder(this, token).buildAsync()

        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()

                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayback()
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        val name = mediaItem?.mediaMetadata?.title
                        if (name != null) {
                            status.text = "يعمل الآن: $name"
                        }
                        updatePlayback()
                    }
                })

                status.text = "محرك الموسيقى جاهز"
            } catch (e: Exception) {
                status.text = "تعذر الاتصال بمحرك الموسيقى"
            }
        }, Executor { it.run() })
    }

    private fun checkPermission() {
        val permission =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
        } else {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadSongs()
        } else if (requestCode == 100) {
            status.text = "يجب السماح بالوصول إلى ملفات الموسيقى"
        }
    }

    private fun loadSongs() {
        songs.clear()
        songList.removeAllViews()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(titleColumn) ?: "Unknown"

                val uri = android.content.ContentUris.withAppendedId(
                    collection, id
                )

                songs.add(Song(name, uri))
            }
        }

        if (songs.isEmpty()) {
            status.text = "لم يتم العثور على أغاني في جهازك"
            return
        }

        status.text = "تم العثور على ${songs.size} أغنية"
        updateSelectionStatus()

        songs.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 5, 8, 5)
                setBackgroundColor(cardColor)
            }

            val play = TextView(this).apply {
                text = "♫  ${song.title}"
                textSize = 15f
                setTextColor(white)
                setPadding(12, 14, 8, 14)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    0, -2, 1f
                )
                setOnClickListener {
                    playSong(index)
                }
            }

            val select = CheckBox(this).apply {
                buttonTintList = ColorStateList.valueOf(blue)
                isChecked = selectedSongs.contains(song.uri.toString())
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedSongs.add(song.uri.toString())
                    } else {
                        selectedSongs.remove(song.uri.toString())
                    }
                    updateSelectionStatus()
                }
            }

            val favorite = Button(this).apply {
                text = if (favorites.contains(song.uri.toString())) "★" else "☆"
                textSize = 20f
                setTextColor(blue)
                backgroundTintList =
                    ColorStateList.valueOf(cardColor)
                setOnClickListener {
                    toggleFavorite(song)
                    text = if (favorites.contains(song.uri.toString())) "★" else "☆"
                }
            }

            row.addView(select)
            row.addView(play)
            row.addView(favorite)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 4, 0, 4)
            songList.addView(row, params)
        }
    }

    private fun updateSelectionStatus() {
        if (::selectionStatus.isInitialized) {
            selectionStatus.text = "المحدد: ${selectedSongs.size}"
        }
        if (::selectAllButton.isInitialized) {
            selectAllButton.text =
                if (songs.isNotEmpty() && selectedSongs.size == songs.size)
                    "إلغاء الكل" else "تحديد الكل"
        }
    }

    private fun playSong(index: Int) {
        val p = controller
        if (p == null) {
            status.text = "انتظر حتى يجهز محرك الموسيقى"
            return
        }

        if (index !in songs.indices) return

        val items = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .build()
                )
                .build()
        }

        p.setMediaItems(items)
        p.prepare()
        p.seekTo(index, 0)
        p.play()

        status.text = "يعمل الآن: ${songs[index].title}"
        updatePlayback()
    }

    private fun toggleFavorite(song: Song) {
        val key = song.uri.toString()

        if (favorites.contains(key)) {
            favorites.remove(key)
        } else {
            favorites.add(key)
        }

        getSharedPreferences("tamer_pro", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("favorites", favorites.toSet())
            .apply()
    }

    private fun updatePlayback() {
        playButton.text =
            if (controller?.isPlaying == true) "⏸" else "▶"
    }

    override fun onDestroy() {
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onDestroy()
    }
}
