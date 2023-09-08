package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter

class ImageAdapter(private val imageList: MutableList<ImageItem>, private val context: Context) :
    RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
    private val imageQualityAnalyzer = ImageInference(context, assetManager = context.assets)
    //ImageQualityAnalyzer(context)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView1)
        val processedImageView: ImageView = itemView.findViewById(R.id.imageView2)
        val textView: TextView = itemView.findViewById(R.id.textView)
        val mlInfo: TextView = itemView.findViewById(R.id.info)

        private val scope = CoroutineScope(Dispatchers.Default)

        fun bind(imageItem: ImageItem) {
            textView.text = imageItem.name
            mlInfo.text = when (imageItem.inferenceState) {
                InferenceState.Initial -> "Inference not started"
                InferenceState.InProgress -> "Inference in progress"
                InferenceState.Completed -> "Quality Score: ${imageItem.qualityScore}"
            }

            scope.launch {
                val rotatedBitmap = rotateBitmapAsync(
                    (context as ImageList).getBitmapFromAsset(itemView.context, imageItem.filePath)!!,
                    imageItem.orientation
                )
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(rotatedBitmap.first)
                    processedImageView.setImageBitmap(rotatedBitmap.second)
                    imageQualityAnalyzer.analyze(rotatedBitmap.second, imageItem.name){ score ->
                        imageItem.qualityScore = score
                        imageItem.inferenceState = InferenceState.Completed
                        mlInfo.text = when (imageItem.inferenceState) {
                            InferenceState.Initial -> "Inference not started"
                            InferenceState.InProgress -> "Inference in progress"
                            InferenceState.Completed -> "Quality Score: ${imageItem.qualityScore}"
                        }
                    }
                }
            }


        }
    }

    private suspend fun rotateBitmapAsync(bitmap: Bitmap, orientation: Int): Pair<Bitmap, Bitmap> {
        return withContext(Dispatchers.Default) {
            val matrix = Matrix()
            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(90f)
                    Bitmap.createBitmap(bitmap, 0, 0,  bitmap.width, bitmap.height, matrix, true)
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.postRotate(180f)
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(270f)
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
                else -> bitmap
            }
            Pair(bitmap, rotatedBitmap)
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageItem = imageList[position]
        holder.bind(imageItem)
    }

    override fun getItemCount(): Int {
        return imageList.size
    }

    fun exportCSV(){
        Log.d("image_quality", "<<<<<<<<  start csv export ")
        try {
            ///storage/emulated/0/Android/data/com.yourappname/files/
            val file = File(context.getExternalFilesDir(null), "android_image_quality.csv")
            val writer = FileWriter(file)

            // Write CSV header
            writer.write("Name,Quality Score\n")

            // Write image items to CSV
            for (item in imageList) {
                val row = "${item.name},${item.qualityScore}\n"
                writer.write(row)
            }

            writer.close()
            Log.d("image_quality", "<<<<<<<<  finished csv export ")
            // CSV export successful
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle export error
        }
    }
}
