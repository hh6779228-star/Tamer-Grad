package com.tamergrad.music

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentUris
import android.provider.MediaStore
import android.media.MediaPlayer
import android.net.Uri
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {

    private var player: MediaPlayer? = null
    private val songs = mutableListOf<Pair<String, Uri>>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(15, 15, 25)
        window.navigationBarColor = Color.rgb(15, 15, 25)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 30, 24, 20)
        root.setBackgroundColor(Color.rgb(15, 15, 25))

        val title = TextView(this)
        title.text = "TAMER GRAD"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)
        title.gravity = 17
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "YOUR MUSIC. YOUR WORLD."
        subtitle.textSize = 12f
        subtitle.gravity = 17
        subtitle.setTextColor(Color.LTGRAY)
        root.addView(subtitle)

        status = TextView(this)
        status.text = "اختر أغنيتك واستمتع بالموسيقى"
        status.textSize = 16f
        status.setTextColor(Color.WHITE)
        status.setPadding(0, 30, 0, 20)
        root.addView(status)

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1, 0, 1f
            )
        )

        val stop = Button(this)
        stop.text = "إيقاف الموسيقى"
        stop.setOnClickListener {
            player?.stop()
            player?.release()
            player = null
            status.text = "تم إيقاف الموسيقى"
        }
        root.addView(stop)

        setContentView(root)

        checkPermission()
    }

    private fun checkPermission() {
        val permission =
            if (android.os.Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_AUDIO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED) {
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }

    private fun loadSongs() {
        songs.clear()
        list.removeAllViews()

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

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

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )

            val titleColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(titleColumn)

                val uri = ContentUris.withAppendedId(
                    collection, id
                )

                songs.add(Pair(name, uri))
            }
        }

        if (songs.isEmpty()) {
            status.text = "لم يتم العثور على أغاني في جهازك"
        } else {
            status.text = "عدد الأغاني: ${songs.size}"

            songs.forEachIndexed { index, song ->
                val button = Button(this)
                button.text = "♫  ${song.first}"
                button.setTextColor(Color.WHITE)
                button.setOnClickListener {
                    playSong(index)
                }
                list.addView(button)
            }
        }
    }

    private fun playSong(index: Int) {
        try {
            player?.release()
            player = MediaPlayer.create(this, songs[index].second)

            if (player != null) {
                player?.start()
                status.text = "يعمل الآن: ${songs[index].first}"
            }
        } catch (e: Exception) {
            status.text = "تعذر تشغيل الأغنية"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
