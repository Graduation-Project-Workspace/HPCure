// HomeScreenUpload.kt
package com.example.demoapp

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HomeScreenUpload : AppCompatActivity() {

    private lateinit var mriImage: ImageView
    private var currentImageUri: Uri? = null
    private var imageUris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hs_upload)

        mriImage = findViewById(R.id.mri_image)
        val prevImage: ImageButton = findViewById(R.id.prev_image)
        val nextImage: ImageButton = findViewById(R.id.next_image)
        val alphaCutSlider: SeekBar = findViewById(R.id.alpha_cut_slider)
        val alphaCutValue: TextView = findViewById(R.id.alpha_cut_value)

        currentImageUri = intent.getStringExtra("image_uri")?.let { Uri.parse(it) }
        currentImageUri?.let {
            mriImage.setImageURI(it)
        }

        imageUris = getImageUris()

        prevImage.setOnClickListener {
            val prevUri = getPreviousImageUri(currentImageUri)
            prevUri?.let {
                currentImageUri = it
                mriImage.setImageURI(it)
            }
        }

        nextImage.setOnClickListener {
            val nextUri = getNextImageUri(currentImageUri)
            nextUri?.let {
                currentImageUri = it
                mriImage.setImageURI(it)
            }
        }

        alphaCutSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alphaCutValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Do nothing
            }
        })
    }

    private fun getImageUris(): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(uri)
            }
        }
        return imageUris
    }

    private fun getPreviousImageUri(currentUri: Uri?): Uri? {
        currentUri ?: return null
        val currentIndex = imageUris.indexOf(currentUri)
        return if (currentIndex > 0) imageUris[currentIndex - 1] else null
    }

    private fun getNextImageUri(currentUri: Uri?): Uri? {
        currentUri ?: return null
        val currentIndex = imageUris.indexOf(currentUri)
        return if (currentIndex < imageUris.size - 1) imageUris[currentIndex + 1] else null
    }
}