package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ImageList : AppCompatActivity() {
    companion object {
        private const val TAG = "image_quality"
    }
    private lateinit var recyclerView: RecyclerView
    private val imageNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        val recyclerView: RecyclerView = findViewById(R.id.rvImages)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val imageList = getImagesFromAssets()
        val adapter = ImageAdapter(imageList, this)
        recyclerView.adapter = adapter

        val exportButton : Button = findViewById(R.id.exportButton)
        exportButton.setOnClickListener {
            adapter.exportCSV()
//            exportToCsv()
        }
    }

//    private fun exportToCsv() {
//        // Logic to export image items to CSV file
//        // You can call a function from ImageQualityAnalyzer class to perform the export
//    }

    // Load bitmap from assets folder
    fun getBitmapFromAsset(context: Context, filePath: String): Bitmap? {
        return try {
            val inputStream = context.assets.open(filePath)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // Get list of image items from assets
    private fun getImagesFromAssets(): MutableList<ImageItem> {
        val imageList = mutableListOf<ImageItem>()
        val assetManager = assets
        try {
            val fileList = assetManager.list("test_images")
            if (fileList != null) {
                for (file in fileList) {
                    val inputStream = assetManager.open("test_images/$file")
                    val orientation = getExifOrientation(inputStream)
                    Log.d(TAG, "***** $file has orientation = $orientation")
                    imageList.add(ImageItem(file, "test_images/$file", orientation))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return imageList
    }

    private fun getExifOrientation(inputStream: InputStream): Int {
        val exif = ExifInterface(inputStream)
        val or = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
        Log.d(TAG, "<<<<<<<<< or is  $or >>>>>>>>>")
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

}