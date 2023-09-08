package com.example.myapplication

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import com.example.myapplication.ml.TfliteModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.core.Size
import org.tensorflow.lite.DataType
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import android.graphics.Canvas
import android.graphics.Matrix
import java.io.FileWriter

//import org.tensorflow.lite.DataType
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.ops.NormalizeOp

fun Bitmap.scaledBitmap(newWidth: Int, newHeight: Int): Bitmap? {
    val scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(scaledBitmap)
    val matrix = Matrix()

    val scaleX = newWidth.toFloat() / width
    val scaleY = newHeight.toFloat() / height
    matrix.setScale(scaleX, scaleY)

    canvas.drawBitmap(this, matrix, null)
    return scaledBitmap
}

class ImageInference(private val context: Context, private val assetManager: AssetManager) {
    private val inputSize = intArrayOf(512, 256, 3)
    private val meanValues = floatArrayOf(118.03f, 112.65f, 108.60f)
    private val stdValues = floatArrayOf(70.75f, 71.51f, 73.11f)

//    private var interpreter: Interpreter

    init {
        val options = Interpreter.Options()
//        interpreter = Interpreter(loadModelFile("tflite_model.tflite"), options)
    }

    //    fun runInference(bitmap: Bitmap): Float {
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize[1], inputSize[0], false)
//        val normalizedBuffer = preprocessImage(resizedBitmap)
//
//        val output = Array(1) { FloatArray(1) }
//        interpreter.run(normalizedBuffer, output)
//
//        val score =  output[0][0]
//        return  score
//    }
    fun saveFloatArrayToFile(array: FloatArray, filePath: String) {
        val outputFile = File(context.filesDir, filePath)
//        val outputFile = File(filePath)
//        outputFile.bufferedWriter().use { writer ->
//            array.forEachIndexed { index, value ->
//                writer.write(value.toString())
//                if (index < array.size - 1) {
//                    writer.write(",")
//                }
//            }
//        }
        val writer = FileWriter(outputFile)

        // Writing the FloatArray data to the CSV
        for (value in array) {
            writer.append(value.toString())
            writer.append(",")
        }
        writer.append("\n")

        writer.flush()
        writer.close()
    }

    //means=(118.03, 112.65, 108.60)
    //    stds=(70.75, 71.51, 73.11)


    fun preprocessv3(bitmap: Bitmap): FloatArray {
        val channelOrientation = BitmapUtils.getChannelOrientation(bitmap)
        println("Bitmap channel orientation: $channelOrientation")
        val scaledBitmap = bitmap.scaledBitmap(256, 512)
        val image = Mat()
        Utils.bitmapToMat(scaledBitmap, image)

        val newHeight = 512
        val newWidth = 256
        val newSize = Size(newWidth.toDouble(), newHeight.toDouble())
//        Imgproc.resize(image, image, newSize, 0.0, 0.0, Imgproc.INTER_LINEAR)

        val means = doubleArrayOf(108.60, 112.65, 118.03) // ARGB_8888 channel orientation
        val stds = doubleArrayOf(73.11, 71.51, 70.75) // ARGB_8888 channel orientation

        val floatValues = FloatArray(image.width() * image.height() * 3) // Four channels
        var counter = 0
        for (y in 0 until image.height()) {
            for (x in 0 until image.width()) {
                val pixel = image.get(y, x)
                for (c in 0 until 3) { // Four channels: Alpha, Red, Green, Blue
                    floatValues[counter++] = ((pixel[c] - means[c]) / stds[c]).toFloat()
                }
            }
        }

        return floatValues
    }

    fun preprocessImagev4(bitmap1: Bitmap,
                        means: FloatArray = floatArrayOf(118.03f, 112.65f, 108.60f),
                        stds: FloatArray = floatArrayOf(70.75f, 71.51f, 73.11f),
                        newWidth: Int = 256,
                        newHeight: Int = 512): FloatArray {
        val channelOrientation = BitmapUtils.getChannelOrientation(bitmap1)
        println("Bitmap channel orientation: $channelOrientation")
        val scaledBitmap1 = bitmap1.scaledBitmap(newWidth, newHeight)!!
        scaledBitmap1.saveBitmapAsCSV(context, "android_scaled_pv4_scaledBitmap1.csv")
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap1, newWidth, newHeight, true)
        scaledBitmap.saveBitmapAsCSV(context, "android_scaled_pv4.csv")
//            bitmap1.scaledBitmap(newWidth, newHeight)!!

        // Convert Bitmap to ARGB and then drop the alpha channel
        val intValues = IntArray(newWidth * newHeight)
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        val floatValues = FloatArray(newWidth * newHeight * 3)

        for (i in 0 until intValues.size) {
            val value = intValues[i]
            floatValues[i * 3 + 0] = ((value shr 16) and 0xFF) - means[0]
            floatValues[i * 3 + 1] = ((value shr 8) and 0xFF) - means[1]
            floatValues[i * 3 + 2] = (value and 0xFF) - means[2]

            floatValues[i * 3 + 0] /= stds[0]
            floatValues[i * 3 + 1] /= stds[1]
            floatValues[i * 3 + 2] /= stds[2]
        }

        return floatValues
    }

    fun preprocessImage(inputBitmap: Bitmap): FloatArray {
        // Convert Bitmap to OpenCV Mat
        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)

        // Resize image
        val newWidth = 512
        val newHeight = 256
        val resizedMat = Mat()
        Imgproc.resize(inputMat, resizedMat, Size(newWidth.toDouble(), newHeight.toDouble()))

        // Convert Mat to FloatArray and normalize
        val outputArray = FloatArray(newWidth * newHeight * 3)
        resizedMat.convertTo(resizedMat, CvType.CV_32FC3) // Convert to 3 channels (RGB)
        resizedMat.get(0, 0, outputArray)

        val means = floatArrayOf(108.60f, 112.65f, 118.03f)
        val stds = floatArrayOf(73.11f, 71.51f, 70.75f)

        for (i in 0 until outputArray.size) {
            outputArray[i] = (outputArray[i] - means[i % 3]) / stds[i % 3]
        }


//        return outputArray

        // Print the number of elements in the outputArray
        println("Number of elements in outputArray: ${outputArray.size}")

        return outputArray
    }

//    fun performInference(inputData: FloatArray): Float {
////        val gpuDelegate: GpuDelegate? = null  // No GPU delegate, so inference is done on CPU
//        val modelPath = "tflite_model.tflite"  // Replace with the actual model path
//        val modelBuffer = loadModelFile(modelPath, assetManager)
//
////        val gpuDelegate: GpuDelegate? = null  // No GPU delegate, so inference is done on CPU
//        val options = Interpreter.Options()
//        options.setUseNNAPI(false)  // Disable NNAPI for compatibility
////        options.addDelegate(gpuDelegate)
//
//        val model = Interpreter(modelBuffer, options)
//
//        // Get the input and output tensor details
//        val inputTensor = model.getInputTensor(0)
//        val outputTensor = model.getOutputTensor(0)
//
//        // Prepare the input tensor data
//        val inputBuffer = ByteBuffer.allocateDirect(inputData.size * 4) // 4 bytes per float
//        inputBuffer.order(ByteOrder.nativeOrder())
//        inputBuffer.rewind()
//        inputBuffer.asFloatBuffer().put(inputData)
//
//        // Allocate buffer for the output tensor (a single float)
//        val outputBuffer = ByteBuffer.allocateDirect(4) // 4 bytes for a single float
//        outputBuffer.order(ByteOrder.nativeOrder())
//        outputBuffer.rewind()
//
//        // Run inference
//        model.run(inputBuffer, outputBuffer)
//
//        // Process the output tensor and return a float value
//        outputBuffer.rewind()
//
//        val score =  outputBuffer.asFloatBuffer().get()
//        return score
//    }

    fun floatArrayToByteBuffer(floatArray: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(floatArray.size * 4) // 4 bytes per float
        buffer.order(ByteOrder.nativeOrder())

        for (value in floatArray) {
            buffer.putFloat(value)
        }

        buffer.rewind()
        return buffer
    }

    fun byteBufferToFloatArray(buffer: ByteBuffer): FloatArray {
        val floatArray = FloatArray(buffer.remaining() / 4)
        buffer.order(ByteOrder.nativeOrder())

        for (i in floatArray.indices) {
            floatArray[i] = buffer.getFloat()
        }

        return floatArray
    }


    fun performInference(inputData: FloatArray) : Float{
        val input = floatArrayToByteBuffer(inputData)
        val inputFloatArray = byteBufferToFloatArray(input)

        val model = TfliteModel.newInstance(context)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 512, 256, 3),  DataType.FLOAT32)
        inputFeature0.loadBuffer(input)
        val outputs = model.process(inputFeature0)

        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val outputArray = outputFeature0.floatArray
        val qualityScore = outputArray[0]

        return qualityScore
    }


    fun getFileNameWithoutExtension(filePath: String): String {
        val fileNameWithExtension = filePath.substringAfterLast('/') // Get the filename with extension
        return fileNameWithExtension.substringBeforeLast('.') // Get the filename without extension
    }

    fun runInference(inputBitmap: Bitmap, name: String?): Float {


//        val outputBitmap = preprocessv3(inputBitmap)
        val outputBitmap = preprocessImagev4(inputBitmap)
        var outputFilePath = "kotlin_preprocessed_v4.csv"
        name?.let { unwrappedName ->
            outputFilePath = getFileNameWithoutExtension(unwrappedName)+".csv"
        }
        saveFloatArrayToFile(outputBitmap, outputFilePath)
//        (context as MainActivity).saveBitmapToFile(outputBitmap, "kotlin_preprocessed.jpeg", context)
//
        val score = performInference(outputBitmap)
        return score// preprocessedImage.bitmap
    }

//    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
//        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize[0] * inputSize[1] * inputSize[2])
//        inputBuffer.order(ByteOrder.nativeOrder())
//
//        for (y in 0 until inputSize[0]) {
//            for (x in 0 until inputSize[1]) {
//                val pixel = bitmap.getPixel(x, y)
//                inputBuffer.putFloat(((pixel shr 16 and 0xFF) - meanValues[0]) / stdValues[0])
//                inputBuffer.putFloat(((pixel shr 8 and 0xFF) - meanValues[1]) / stdValues[1])
//                inputBuffer.putFloat(((pixel and 0xFF) - meanValues[2]) / stdValues[2])
//            }
//        }
//        return inputBuffer
//    }

    private fun loadModelFile(modelFile: String, assetManager: AssetManager): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun analyze(bitmap: Bitmap, name: String?, completion: (Float)  -> Unit) {
        // Update quality score and inference state

        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch {

            val score = runInference(bitmap, name)
            withContext(Dispatchers.Main) {
                completion(score)
                // Recycle the resized bitmap
//                resizedBitmap.recycle()
            }
        }
//        return imageItem
    }
}
