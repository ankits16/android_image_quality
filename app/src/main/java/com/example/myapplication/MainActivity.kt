package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import com.example.myapplication.ml.TfliteModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.nio.MappedByteBuffer
import android.content.res.AssetManager
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlinx.coroutines.*
import android.graphics.Bitmap.CompressFormat
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Base64
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.CvType.CV_32FC3
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Dnn.blobFromImage
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.opencv.android.Utils
import java.io.FileDescriptor
import org.tensorflow.lite.support.image.TensorImage
import java.io.FileWriter

fun Bitmap.saveBitmapAsCSV(context: Context, filePath: String) {
    val width = width
    val height = height
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val outputFile = File(context.filesDir, filePath)
    val writer = FileWriter(outputFile)
    for (y in 0 until height) {
        val sb = StringBuilder()
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            if (x > 0) {
                sb.append(',')
            }
            sb.append("$red,$green,$blue")
        }
        writer.write(sb.toString())
        writer.write("\n")
    }
    Log.d("image_quality","<<<<<<, saveBitmapAsCSV $filePath")
    writer.close()
}

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "image_quality"
    }
    private var imageUri: Uri? = null
    private lateinit var assetManager: AssetManager
//    private var interpreter : Interpreter? = null

    private lateinit var interpreter: Interpreter
   private fun preProcessImage(bitmap: Bitmap): ByteBuffer {
       Log.d("image_quality","<<<<<<, preProcessImage")
       val input = ByteBuffer.allocateDirect(256*512*3*4).order(ByteOrder.nativeOrder())
       val means  = listOf<Float>(118.03860628335003F, 112.65675931587339F, 108.60966603551644F)// []
       val stds  = listOf<Float>(70.75601041091608F, 71.51498874856256F, 73.11152587776891F)
       for (y in 0 until 512) {
           for (x in 0 until 256) {
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
//       val input = ByteBuffer.allocateDirect(224*224*3*4).order(ByteOrder.nativeOrder())
   }

    fun convertByteBufferToBitmap(byteBuffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        byteBuffer.rewind()
        val intArray = IntArray(width * height)
        for (i in intArray.indices) {
//            val normalizedRed = (byteBuffer.getFloat() * 255).toInt()
//            val normalizedGreen = (byteBuffer.getFloat() * 255).toInt()
//            val normalizedBlue = (byteBuffer.getFloat() * 255).toInt()
            val normalizedRed = (byteBuffer.getFloat() * 255.0f).coerceIn(0.0f, 255.0f).toInt()
            val normalizedGreen = (byteBuffer.getFloat() * 255.0f).coerceIn(0.0f, 255.0f).toInt()
            val normalizedBlue = (byteBuffer.getFloat() * 255.0f).coerceIn(0.0f, 255.0f).toInt()


            val color = (0xFF shl 24) or
                    (normalizedRed and 0xFF) shl 16 or
                    (normalizedGreen and 0xFF) shl 8 or
                    (normalizedBlue and 0xFF)

            intArray[i] = color
        }
        bitmap.setPixels(intArray, 0, width, 0, 0, width, height)
        return bitmap
    }


    private fun calculateOutputSize(interpreter: Interpreter): Int {
        val outputTensorIndex = 0 // Replace with the index of your desired output tensor
        val outputTensor = interpreter.getOutputTensor(outputTensorIndex)
        val outputShape = outputTensor.shape()
        val outputDataType = outputTensor.dataType()
        return outputShape.reduce { acc, value -> acc * value } * outputDataType.byteSize()
    }

    private fun callProcessImage(bitmap: Bitmap, onComplete: (Float) -> Unit) {
//        val inputChannels = 3 // Number of color channels (RGB)
//        val newWidth = 256 // Use the original newWidth from your Python code
//        val newHeight = 512 // Use the original newHeight from your Python code
//
//        val means = floatArrayOf(118.03f, 112.65f, 108.60f) // Hardcoded mean values
//        val stds = floatArrayOf(70.75f, 71.51f, 73.11f) // Hardcoded standard deviation values
//
//        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
//        saveBitmapToFile(resizedBitmap, "resized_image_python.png", this@MainActivity)

        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch {
            val outputArray = runInference(bitmap) //newPreProcessImage(resizedBitmap)
            withContext(Dispatchers.Main) {
                onComplete(outputArray)
            }
        }
    }

    fun saveBitmapToFile(bitmap: Bitmap, filename: String, context: Context) {
        val file = File(context.filesDir, filename)

        try {
            val outStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            outStream.flush()
            outStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun newPreProcessImage(bitmap: Bitmap): FloatArray{
        val inputChannels = 3 // Number of color channels (RGB)
        val newWidth = 256 // Use the original newWidth from your Python code
        val newHeight = 512 // Use the original newHeight from your Python code

        val means = floatArrayOf(118.03f, 112.65f, 108.60f) // Hardcoded mean values
        val stds = floatArrayOf(70.75f, 71.51f, 73.11f) // Hardcoded standard deviation values

//        val inputBuffer = ByteBuffer.allocateDirect(inputChannels * newWidth * newHeight * DataType.FLOAT32.byteSize())
//        inputBuffer.order(ByteOrder.nativeOrder())

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        var inputBuffer = preProcessImage(resizedBitmap)

        GlobalScope.launch( Dispatchers.Main ) {
            val pickedImageView = findViewById<ImageView>(R.id.ivAnalysedImage)
            val preprocessedBitmap = convertByteBufferToBitmap(inputBuffer, newWidth, newHeight)
            pickedImageView.setImageBitmap(preprocessedBitmap)
            saveBitmapToFile(preprocessedBitmap, "preprocessed.png", this@MainActivity)
            performInference(inputBuffer)
        }


        val outputSize = calculateOutputSize(interpreter)
        val outputBuffer = ByteBuffer.allocateDirect(outputSize)
        interpreter.run(inputBuffer, outputBuffer)

        val outputArray = FloatArray(outputSize / DataType.FLOAT32.byteSize())
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputArray)


        return outputArray
    }

    private fun performInference(input: ByteBuffer){
        Log.d("image_quality","<<<<<<, performInference")
//        if (interpreter == null){
//
//            val modelFile = FileUtil.loadMappedFile(this, "tflite_model.tflite")
//            val options = Interpreter.Options()
//            interpreter = Interpreter(modelFile, options)
//            Log.d(TAG, "created interpreter")
//        }

        val model = TfliteModel.newInstance(this)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 512, 256, 3),  DataType.FLOAT32)
        inputFeature0.loadBuffer(input)
        val outputs = model.process(inputFeature0)

        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val tvScore = findViewById<TextView>(R.id.tvScore)
        tvScore.text = Arrays.toString(outputFeature0.floatArray)
//        preProcessImage()
//        val bufferSize = 1 * java.lang.Float.SIZE / java.lang.Byte.SIZE
//        val modelOutput = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
//        interpreter?.run(input, modelOutput)
////        interpreter.run(input, modelOutput)
//        modelOutput.rewind()
//        Log.d(TAG, "inference completed")
//        try {
//            val probabilities = Arrays.toString(modelOutput.array())
//            Log.d(TAG, "interpreter already there so continue with inference " + probabilities)
//        }catch(e : Exception){
//            Log.d(TAG, "execption while converting buffer to array" + e.toString())
//        }

//        for (i in probabilities.capacity()) {
//            val probability = probabilities.get(i)
//            Log.d(TAG, "interpreter already there so continue with inference")
//        }

        Log.d(TAG, "interpreter already there so continue with inference")
    }

    fun runModel(inputBuffer: ByteBuffer): FloatArray {
        val model = interpreter
        val outputTensorIndex = 0 // Replace with the index of your desired output tensor

        val outputTensor = model!!.getOutputTensor(outputTensorIndex)
        val outputShape = outputTensor.shape()
        val outputDataType = outputTensor.dataType()

        val outputSize = outputShape.reduce { acc, value -> acc * value } * outputDataType.byteSize()

        val outputBuffer = ByteBuffer.allocateDirect(outputSize)
        model.run(inputBuffer, outputBuffer)

        val outputArray = FloatArray(outputShape[outputShape.size - 1]) // Assuming last dimension is channels
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputArray)

        return outputArray
    }

    fun getOrientation(photoUri: Uri?): Int {
        val cursor = this.contentResolver.query(
            photoUri!!,
            arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
            null,
            null,
            null
        )
        if (cursor == null || cursor.count != 1) {
            return 90 //Assuming it was taken portrait
        }
        cursor.moveToFirst()
        return cursor.getInt(0)
    }


    @Throws(IOException::class)
    fun getCorrectlyOrientedImage( photoUri: Uri?, maxWidth: Int): Bitmap? {
        var `is` = this.contentResolver.openInputStream(photoUri!!)
        val dbo = BitmapFactory.Options()
        dbo.inJustDecodeBounds = true
        BitmapFactory.decodeStream(`is`, null, dbo)
        `is`!!.close()
        val rotatedWidth: Int
        val rotatedHeight: Int
        val orientation: Int = getOrientation(photoUri)
        if (orientation == 90 || orientation == 270) {
            Log.d("ImageUtil", "Will be rotated")
            rotatedWidth = dbo.outHeight
            rotatedHeight = dbo.outWidth
        } else {
            rotatedWidth = dbo.outWidth
            rotatedHeight = dbo.outHeight
        }
        var srcBitmap: Bitmap?
        `is` = this.contentResolver.openInputStream(photoUri)
        Log.d(
            "ImageUtil", String.format(
                "rotatedWidth=%s, rotatedHeight=%s, maxWidth=%s",
                rotatedWidth, rotatedHeight, maxWidth
            )
        )
        if (rotatedWidth > maxWidth || rotatedHeight > maxWidth) {
            val widthRatio = rotatedWidth.toFloat() / maxWidth.toFloat()
            val heightRatio = rotatedHeight.toFloat() / maxWidth.toFloat()
            val maxRatio = Math.max(widthRatio, heightRatio)
            Log.d(
                "ImageUtil", String.format(
                    "Shrinking. maxRatio=%s",
                    maxRatio
                )
            )

            // Create the bitmap from file
            val options = BitmapFactory.Options()
            options.inSampleSize = maxRatio.toInt()
            srcBitmap = BitmapFactory.decodeStream(`is`, null, options)
        } else {
            Log.d(
                "ImageUtil", String.format(
                    "No need for Shrinking. maxRatio=%s",
                    1
                )
            )
            srcBitmap = BitmapFactory.decodeStream(`is`)
            Log.d("ImageUtil", String.format("Decoded bitmap successful"))
        }
        `is`!!.close()

        /*
         * if the orientation is not 0 (or -1, which means we don't know), we
         * have to do a rotation.
         */if (orientation > 0) {
            val matrix = Matrix()
            matrix.postRotate(orientation.toFloat())
            srcBitmap = Bitmap.createBitmap(
                srcBitmap!!, 0, 0, srcBitmap.width,
                srcBitmap.height, matrix, true
            )
        }
        return srcBitmap
    }
    fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun encodeToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private val receiveImageFromGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if (it.resultCode == RESULT_OK){
            Log.d(TAG,"<<<<<<, image picked successfully")
            val pickedImageView = findViewById<ImageView>(R.id.ivAnalysedImage)

            imageUri = it.data?.data
            var inpStream = imageUri?.let { it1 -> contentResolver.openInputStream(it1) }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(inpStream, null, options)
            val channelOrientation = BitmapUtils.getChannelOrientation(bitmap!!)
            println("Bitmap channel orientation read from gallery: $channelOrientation")
//            val exif = ExifInterface(inpStream!!)
//            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            var orientation2: Int = getOrientation(imageUri)
            val tvScore = findViewById<TextView>(R.id.tvScore)
            Log.d(TAG, "********** orientation2 is: $orientation2")
            bitmap.saveBitmapAsCSV (this, "android_original.csv")
            val rotatedBitmap = rotateBitmap(bitmap!!, orientation2)
            rotatedBitmap.saveBitmapAsCSV(this, "android_original_rotated.csv")
            val channelOrientationrot = BitmapUtils.getChannelOrientation(rotatedBitmap)
            println("Bitmap channel orientation rotated image: $channelOrientationrot")
//            val rotatedBitmap = when (orientation2) {
//                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap!!, 90f)
//                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap!!, 180f)
//                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap!!, 270f)
//                else -> bitmap
//            }
            pickedImageView.setImageBitmap(rotatedBitmap!!)
//            val base64String = encodeToBase64(rotatedBitmap!!)
//            Log.d(TAG, "Base64 Image: $base64String")
            inpStream?.close()

//            val outputArray = newPreProcessImage(bitmap)
            callProcessImage(rotatedBitmap!!){ outputArray ->
                // This code will run when the preprocessing is complete
                // Handle the outputArray here (e.g., run inference and display results)

                tvScore.text = "here $outputArray"
                print("here $outputArray")
            }

        }
    }

    /*fun runInference(bitmap: Bitmap): Float {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

        val newHeight = 512
        val newWidth = 256
//
//        // Resize the input Bitmap using OpenCV
//        val resizedBitmap = Mat()
//        Utils.bitmapToMat(bitmap, resizedBitmap)
//        Imgproc.resize(resizedBitmap, resizedBitmap, Size(newWidth.toDouble(), newHeight.toDouble()))

//        // Preprocess the image using OpenCV
//        val means = Scalar(118.03, 112.65, 108.60)
//        val stds = Scalar(70.75, 71.51, 73.11)
//
//        val normalizedImage = Mat()
//        Core.subtract(resizedBitmap, means, normalizedImage)
//        Core.divide(normalizedImage, stds, normalizedImage)
//
//        val inputBuffer = ByteBuffer.allocateDirect(4 * newWidth * newHeight * 3)
//        inputBuffer.order(ByteOrder.nativeOrder())
//
//        // Flatten the normalizedImage matrix and put the values directly into inputBuffer
//        for (row in 0 until newHeight) {
//            for (col in 0 until newWidth) {
//                val pixel = normalizedImage.get(row, col)
//                inputBuffer.putFloat(pixel[0].toFloat())
//                inputBuffer.putFloat(pixel[1].toFloat())
//                inputBuffer.putFloat(pixel[2].toFloat())
//            }
//        }
//
//
//        val outputBuffer = ByteBuffer.allocateDirect(4)
//        outputBuffer.order(ByteOrder.nativeOrder())
//        val modelFile = "tflite_model.tflite"
//        interpreter = Interpreter(loadModelFile(modelFile, assets))
//        interpreter.run(inputBuffer, outputBuffer)
//
//        val score =  outputBuffer.getFloat(0)
//        return  score
        // Resize the input bitmap
        val inputSize = 3 * newWidth * newHeight
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false)

        // Convert the resized Bitmap to a float array for inference
        val inputArray = FloatArray(inputSize)
        val imageMat = Mat(newHeight, newWidth, CvType.CV_32FC3)  // Use CV_32FC3 for floating-point values
        Utils.bitmapToMat(resizedBitmap, imageMat)

        // Normalize the input using means and stds
        val means = floatArrayOf(118.03f, 112.65f, 108.60f)
        val stds = floatArrayOf(70.75f, 71.51f, 73.11f)
        var index = 0
        for (row in 0 until newHeight) {
            for (col in 0 until newWidth) {
                val pixel = imageMat.get(row, col)
                for (channel in 0 until 3) {
                    inputArray[index] = (pixel[channel].toFloat() - means[channel]) / stds[channel]
                    index++
                }
            }
        }

        // Load the TensorFlow Lite model
        val model = Interpreter(loadModelFile("tflite_model.tflite", assets))

        // Allocate buffers for input and output tensors
        val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(inputSize * 4)  // 4 bytes for float
        inputBuffer.order(java.nio.ByteOrder.nativeOrder())
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(inputArray)

        val outputSize = 4  // Assuming output is a single float
        val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(outputSize * 4)  // 4 bytes for float
        outputBuffer.order(java.nio.ByteOrder.nativeOrder())
        outputBuffer.rewind()

        // Run inference
        model.run(inputBuffer, outputBuffer)

        // Get the inference result
        outputBuffer.rewind()
        val result = outputBuffer.asFloatBuffer().get()
        return  result
    }*/

    fun runInference(bitmap: Bitmap): Float {
        return ImageInference(this, assets).runInference(bitmap, null) //(0.0).toFloat()
    }

    fun BitmapToFloatArray(bitmap: Bitmap, means: FloatArray, stds: FloatArray): FloatArray {
        val imageMat = Mat(bitmap.height, bitmap.width, CvType.CV_32FC3)
        Utils.bitmapToMat(bitmap, imageMat)
        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_RGBA2RGB)

        val floatArray = FloatArray(bitmap.width * bitmap.height * 3)
        var index = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = imageMat.get(y, x)
                floatArray[index++] = (pixel[0].toFloat() - means[0]) / stds[0]
                floatArray[index++] = (pixel[1].toFloat() - means[1]) / stds[1]
                floatArray[index++] = (pixel[2].toFloat() - means[2]) / stds[2]
            }
        }

        return floatArray
    }


    private fun loadModelFile(modelFile: String, assetManager: AssetManager): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private val WRITE_EXTERNAL_STORAGE_REQUEST = 123
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if(OpenCVLoader.initDebug()){

            System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
            Log.d("myapp", "<<<<<<<<< Open Cv is initialized")
            println("OpenCV version: ${Core.VERSION}")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_EXTERNAL_STORAGE_REQUEST
            )
        } else {
            // Permission already granted, proceed with your code
        }

        val modelFile = "tflite_model.tflite"
        interpreter = Interpreter(loadModelFile(modelFile, assets))

        val openListBtn = findViewById<View>(R.id.btnOpenImageList)
        openListBtn.setOnClickListener {
            Intent(this, ImageList::class.java).also {
                startActivity(it)
            }
        }

        val openImageBtn = findViewById<Button>(R.id.btnOpenImage)
        openImageBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setType("image/*")
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            receiveImageFromGallery.launch(intent)
//            startActivityForResult(gallery, pickImage)
        }

        val openTextBtn = findViewById<Button>(R.id.btnReadTextFile)
        openTextBtn.setOnClickListener {
            var file_name = "2345.txt"
            val bufferReader = application.assets.open(file_name).bufferedReader()
            val data = bufferReader.use {
                it.readText()
            }
            Log.d(TAG, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with your code
                Log.d(TAG, "Permission granted, proceed with your code")
            } else {
                // Permission denied, handle accordingly
                Log.d(TAG, "Permission denied, handle accordingly")
            }
        }
    }
}