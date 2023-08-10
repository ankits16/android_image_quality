package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.example.myapplication.ml.TfliteModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

class ImageQualityAnalyzer(private val context: Context) {
//    private val model = TfliteModel.newInstance(context)
    val inputChannels = 3 // Number of color channels (RGB)
    private val newWidth = 256 // Use the original newWidth from your Python code
    private val newHeight = 512 // Use the original newHeight from your Python code
    fun analyze(bitmap: Bitmap, completion: (Float)  -> Unit) {
        // Update quality score and inference state

        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch {

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            var inputBuffer = preProcessImage(resizedBitmap)
            val score = performInference(inputBuffer)
            withContext(Dispatchers.Main) {
                completion(score)
                // Recycle the resized bitmap
                resizedBitmap.recycle()
            }
        }
//        return imageItem
    }

    /*
    * This function takes a Bitmap as input and performs preprocessing on it.
    * It iterates over each pixel in the bitmap, normalizes the channel values,
    * and fills a ByteBuffer with the normalized values.
    * */
    private fun preProcessImage(bitmap: Bitmap): ByteBuffer {
        Log.d("image_quality","<<<<<<, preProcessImage")
        val input = ByteBuffer.allocateDirect(newWidth*newHeight*3*4).order(ByteOrder.nativeOrder())
        val means  = listOf<Float>(118.03860628335003F, 112.65675931587339F, 108.60966603551644F)// []
        val stds  = listOf<Float>(70.75601041091608F, 71.51498874856256F, 73.11152587776891F)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val px = bitmap.getPixel(x, y)

                // Get channel values from the pixel value.
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)

                // Normalize channel values to [-1.0, 1.0]. This requirement depends on the model.
                // For example, some models might require values to be normalized to the range
                // [0.0, 1.0] instead.
                val rf = (r - means[0]) / stds[0]
                val gf = (g - means[1]) / stds[1]
                val bf = (b - means[2]) / stds[2]

                input.putFloat(rf)
                input.putFloat(gf)
                input.putFloat(bf)
            }
        }
        return input
    }

    /*
    * This function takes a ByteBuffer as input and performs inference using the
    * TensorFlow Lite model. It creates a TensorBuffer from the input data, processes it through
    * the model, and extracts the quality score from the output.
    * */
    private fun performInference(input: ByteBuffer) : Float{
        Log.d("image_quality","<<<<<<, performInference")
        val model = TfliteModel.newInstance(context)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 512, 256, 3),  DataType.FLOAT32)
        inputFeature0.loadBuffer(input)
        val outputs = model.process(inputFeature0)

        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val outputArray = outputFeature0.floatArray
        val qualityScore = outputArray[0]

        return qualityScore
    }
}