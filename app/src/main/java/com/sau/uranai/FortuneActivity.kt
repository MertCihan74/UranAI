package com.sau.uranai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sau.uranai.databinding.ActivityFortuneBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import io.github.cdimascio.dotenv.Dotenv

class FortuneActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FortuneActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_IMAGE_CAPTURE_1 = 1
        private const val REQUEST_IMAGE_CAPTURE_2 = 2
        private const val OPENAI_API_KEY = ""
        private const val MODEL_PATH = "best_float32.tflite"
        private const val IMAGE_SIZE = 640
    }

    private lateinit var binding: ActivityFortuneBinding
    private var tflite: Interpreter? = null
    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var isFirstImageGrounds = false
    private var isSecondImageGrounds = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityFortuneBinding.inflate(layoutInflater)
            setContentView(binding.root)

            initializeModel()

            if (allPermissionsGranted()) {
                setupUI()
            } else {
                requestPermissions()
            }

        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
            showError("Application could not be started: ${e.message}")
        }
    }

    private fun initializeModel() {
        try {
            val modelPath = assetFilePath(this, MODEL_PATH)
            Log.d(TAG, "Model path: $modelPath")

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw Exception("Model file not found: $modelPath")
            }
            Log.d(TAG, "Model file size: ${modelFile.length()} bytes")

            val tfliteModel = loadModelFile(modelFile)
            val tfliteOptions = Interpreter.Options()
            tflite = Interpreter(tfliteModel, tfliteOptions)

            Log.d(TAG, "TFLite model loaded successfully")

            val testInput = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder())

            val testOutput = Array(1) { Array(6) { FloatArray(8400) } }

            tflite?.run(testInput, testOutput)
            Log.d(TAG, "Test output size: [1, 6, 8400]")

        } catch (e: Exception) {
            Log.e(TAG, "Model loading error: ${e.message}", e)
            e.printStackTrace()
            showError("Model could not be loaded: ${e.message}")
        }
    }

    private fun loadModelFile(modelFile: File): MappedByteBuffer {
        val fileInputStream = FileInputStream(modelFile)
        val fileChannel = fileInputStream.channel
        val startOffset = 0L
        val declaredLength = modelFile.length()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun setupUI() {
        binding.apply {
            captureButton1.setOnClickListener {
                takePicture(REQUEST_IMAGE_CAPTURE_1)
            }

            captureButton2.setOnClickListener {
                takePicture(REQUEST_IMAGE_CAPTURE_2)
            }

            analyzeButton.setOnClickListener {
                if (bitmap1 != null && bitmap2 != null) {
                    analyzeImages()
                } else {
                    showError("Please take photos first")
                }
            }
        }
    }

    private fun analyzeImages() {
        try {
            showLoading(true)
            binding.resultTextView.text = "Analyzing photos..."

            isFirstImageGrounds = analyzeWithTFLite(bitmap1!!)
            Log.d(TAG, "First photo analysis result: $isFirstImageGrounds")

            isSecondImageGrounds = analyzeWithTFLite(bitmap2!!)
            Log.d(TAG, "Second photo analysis result: $isSecondImageGrounds")

            checkResults()

        } catch (e: Exception) {
            Log.e(TAG, "Analysis error: ${e.message}", e)
            showLoading(false)
            showError("Error during analysis: ${e.message}")
        }
    }

    private fun analyzeWithTFLite(bitmap: Bitmap): Boolean {
        try {
            val inputBuffer = convertBitmapToByteBuffer(bitmap)
            val outputArray = Array(1) { Array(6) { FloatArray(8400) } }

            tflite?.run(inputBuffer, outputArray)

            var maxConfidence = 0f
            var predictedClass = -1

            for (i in 0 until 8400) {
                val confidence = outputArray[0][4][i]
                val classScore = outputArray[0][5][i]

                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    predictedClass = if (classScore > 0.5f) 1 else 0
                }
            }

            Log.d(TAG, "Highest confidence: $maxConfidence")
            Log.d(TAG, "Predicted class: ${if (predictedClass == 0) "Has grounds" else "No grounds"}")

            return predictedClass == 0 && maxConfidence > 0.5f

        } catch (e: Exception) {
            Log.e(TAG, "TFLite analysis error: ${e.message}", e)
            return false
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        resizedBitmap.getPixels(
            intValues,
            0,
            IMAGE_SIZE,
            0,
            0,
            IMAGE_SIZE,
            IMAGE_SIZE
        )

        var pixel = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }

        return byteBuffer
    }

    private fun getFortuneTelling() {
        try {
            if (!isNetworkAvailable()) {
                showError("Please check your internet connection")
                return
            }

            Log.d(TAG, "Starting fortune telling process...")
            showLoading(true)
            binding.resultTextView.text = "Getting fortune reading..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userData = getUserDataFromFirebase()

                    withContext(Dispatchers.Main) {
                        // Rest of the function remains the same
                        val client = OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build()

                        val requestBody = JSONObject().apply {
                            put("model", "gpt-3.5-turbo")
                            put("messages", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "system")
                                    put("content", """
                                    You are a professional coffee fortune teller. You make your readings according to these rules:

                                    1. Your interpretations are always positive and hopeful.
                                    2. You look at three main topics: Love, Career, and Health.
                                    3. You write a separate paragraph for each topic.
                                    4. Each paragraph contains 3-4 related and meaningful sentences.
                                    5. Sentences complement each other and form a coherent whole.
                                    6. You make your interpretations in English.
                                    7. You only interpret the coffee grounds patterns.
                                    8. Each paragraph starts with "Love:", "Career:", "Health:".
                                    9. You leave one line space between paragraphs.
                                    10. You only make paragraph transitions when changing topics.
                                    11. Incorporate the user's personal information into your reading where relevant.
                                    """.trimIndent())
                                })
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", """
                                    I can see the coffee grounds patterns in the cup. 
                                    Please evaluate the patterns and provide a detailed interpretation 
                                    about love, career, and health. I expect a positive reading with 
                                    3-4 sentences for each topic in separate paragraphs.
                                    
                                    Here is some information about me:
                                    Name: ${userData.name}
                                    Surname: ${userData.surname}
                                    Gender: ${userData.gender}
                                    Date of Birth: ${userData.dateOfBirth}
                                    Zodiac Sign: ${userData.zodiacSign}
                                    Birth City: ${userData.birthCity}
                                    Occupation: ${userData.occupation}
                                    Marital Status: ${userData.maritalStatus}
                                    
                                    Please incorporate this information into your reading where relevant.
                                    """.trimIndent())
                                })
                            })
                            put("temperature", 0.7)
                            put("max_tokens", 1000)
                        }

                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/chat/completions")
                            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                            .addHeader("Content-Type", "application/json")
                            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val response = client.newCall(request).execute()
                                val responseBody = response.body?.string()

                                withContext(Dispatchers.Main) {
                                    if (response.isSuccessful && responseBody != null) {
                                        try {
                                            val jsonResponse = JSONObject(responseBody)
                                            val fortune = jsonResponse
                                                .getJSONArray("choices")
                                                .getJSONObject(0)
                                                .getJSONObject("message")
                                                .getString("content")

                                            val formattedFortune = fortune.replace(". ", ".\n\n")

                                            showLoading(false)
                                            binding.resultTextView.text = formattedFortune
                                        } catch (e: Exception) {
                                            Log.e(TAG, "JSON parsing error", e)
                                            showLoading(false)
                                            showError("Could not process fortune reading: ${e.message}")
                                        }
                                    } else {
                                        val errorMessage = when (response.code) {
                                            401 -> "Invalid API key"
                                            429 -> "Too many requests. Please wait."
                                            500 -> "OpenAI server error"
                                            else -> "API not responding (Code: ${response.code})"
                                        }
                                        showLoading(false)
                                        showError(errorMessage)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    showLoading(false)
                                    when (e) {
                                        is java.net.UnknownHostException ->
                                            showError("Please check your internet connection")
                                        is java.net.SocketTimeoutException ->
                                            showError("Connection timed out. Please try again.")
                                        else ->
                                            showError("Could not get fortune reading: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        showError("Could not retrieve user data: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            showLoading(false)
            showError("Could not start fortune telling: ${e.message}")
        }
    }
    private suspend fun getUserDataFromFirebase(): UserData {
        try {
            // Get the current user
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                throw Exception("No user is currently signed in")
            }

            // Retrieve user data from Firestore
            val userData = FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .get()
                .await()
                .toObject(UserData::class.java)

            if (userData == null) {
                throw Exception("User data not found in Firestore")
            }

            return userData
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user data from Firebase: ${e.message}", e)
            throw e
        }
    }

    private fun checkResults() {
        if (isFirstImageGrounds && isSecondImageGrounds) {
            getFortuneTelling()
        } else {
            showLoading(false)
            val message = buildString {
                append("Not enough coffee grounds detected in photos.\n\n")
                append("1st Photo: ${if (isFirstImageGrounds) "Has grounds ✓" else "No grounds ✗"}\n")
                append("2nd Photo: ${if (isSecondImageGrounds) "Has grounds ✓" else "No grounds ✗"}\n\n")
                append("Please take photos with coffee grounds.")
            }
            binding.resultTextView.text = message
        }
    }

    private fun takePicture(requestCode: Int) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            try {
                intent.putExtra("android.intent.extras.CAMERA_FACING", 0)
                intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                intent.putExtra("android.intent.extra.QUALITY", 100)
                startActivityForResult(intent, requestCode)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start camera: ${e.message}", e)
                showError("Could not start camera: ${e.message}")
            }
        }
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }

        return file.absolutePath
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE_1 -> {
                    bitmap1 = data?.extras?.get("data") as? Bitmap
                    bitmap1?.let { binding.imageView1.setImageBitmap(it) }
                }
                REQUEST_IMAGE_CAPTURE_2 -> {
                    bitmap2 = data?.extras?.get("data") as? Bitmap
                    bitmap2?.let { binding.imageView2.setImageBitmap(it) }
                }
            }
            binding.analyzeButton.isEnabled = (bitmap1 != null && bitmap2 != null)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupUI()
            } else {
                showError("Camera permission is required for the app to work")
                finish()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite?.close()
        tflite = null
        bitmap1?.recycle()
        bitmap2?.recycle()
        bitmap1 = null
        bitmap2 = null
    }
    data class UserData(
        val name: String="",
        val surname: String="",
        val gender: String="",
        val dateOfBirth: String="",
        val zodiacSign: String="",
        val birthCity: String="",
        val occupation: String="",
        val maritalStatus: String=""
    )
}