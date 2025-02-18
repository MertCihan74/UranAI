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

class FortuneActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "FortuneActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_IMAGE_CAPTURE_1 = 1
        private const val REQUEST_IMAGE_CAPTURE_2 = 2
        private const val OPENAI_API_KEY = "your-api-key-here"
        private const val MODEL_PATH = "best_float32.tflite"
        private const val IMAGE_SIZE = 640
    }

    private lateinit var binding: ActivityFortuneBinding
    private var tflite: Interpreter? = null
    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var isFirstImageTelveli = false
    private var isSecondImageTelveli = false

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
            showError("Uygulama başlatılamadı: ${e.message}")
        }
    }

    private fun initializeModel() {
        try {
            val modelPath = assetFilePath(this, MODEL_PATH)
            Log.d(TAG, "Model yolu: $modelPath")

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw Exception("Model dosyası bulunamadı: $modelPath")
            }
            Log.d(TAG, "Model dosya boyutu: ${modelFile.length()} bytes")

            // TFLite modelini yükle
            val tfliteModel = loadModelFile(modelFile)
            val tfliteOptions = Interpreter.Options()
            tflite = Interpreter(tfliteModel, tfliteOptions)

            Log.d(TAG, "TFLite model başarıyla yüklendi")

            // Test input ile modeli dene
            val testInput = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder())

            // Çıktı şeklini [1, 6, 8400] olarak düzelt
            val testOutput = Array(1) { Array(6) { FloatArray(8400) } }

            tflite?.run(testInput, testOutput)
            Log.d(TAG, "Test çıktısı boyutu: [1, 6, 8400]")

        } catch (e: Exception) {
            Log.e(TAG, "Model yükleme hatası: ${e.message}", e)
            e.printStackTrace()
            showError("Model yüklenemedi: ${e.message}")
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
                    showError("Lütfen önce fotoğrafları çekin")
                }
            }
        }
    }

    private fun analyzeImages() {
        try {
            showLoading(true)
            binding.resultTextView.text = "Fotoğraflar analiz ediliyor..."

            isFirstImageTelveli = analyzeWithTFLite(bitmap1!!)
            Log.d(TAG, "İlk fotoğraf analiz sonucu: $isFirstImageTelveli")

            isSecondImageTelveli = analyzeWithTFLite(bitmap2!!)
            Log.d(TAG, "İkinci fotoğraf analiz sonucu: $isSecondImageTelveli")

            checkResults()

        } catch (e: Exception) {
            Log.e(TAG, "Analiz hatası: ${e.message}", e)
            showLoading(false)
            showError("Analiz sırasında hata oluştu: ${e.message}")
        }
    }

    private fun analyzeWithTFLite(bitmap: Bitmap): Boolean {
        try {
            val inputBuffer = convertBitmapToByteBuffer(bitmap)
            // Çıktı array'ini doğru boyutta oluştur
            val outputArray = Array(1) { Array(6) { FloatArray(8400) } }

            // Modeli çalıştır
            tflite?.run(inputBuffer, outputArray)

            // En yüksek güvenilirlik skorunu bul
            var maxConfidence = 0f
            var predictedClass = -1

            // Her tespit için kontrol et
            for (i in 0 until 8400) {
                val confidence = outputArray[0][4][i]  // confidence skoru
                val classScore = outputArray[0][5][i]  // class skoru

                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    predictedClass = if (classScore > 0.5f) 1 else 0
                }
            }

            Log.d(TAG, "En yüksek güvenilirlik: $maxConfidence")
            Log.d(TAG, "Tahmin edilen sınıf: ${if (predictedClass == 0) "Telveli" else "Telvesiz"}")

            // Telveli sınıfı için karar ver
            return predictedClass == 0 && maxConfidence > 0.5f

        } catch (e: Exception) {
            Log.e(TAG, "TFLite analiz hatası: ${e.message}", e)
            return false
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        // getPixels parametreleri düzeltildi
        resizedBitmap.getPixels(
            intValues,        // pixel array
            0,                // offset
            IMAGE_SIZE,       // stride
            0,                // x
            0,                // y
            IMAGE_SIZE,       // width
            IMAGE_SIZE        // height
        )

        var pixel = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)  // Red
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)   // Green
                byteBuffer.putFloat((value and 0xFF) / 255.0f)           // Blue
            }
        }

        return byteBuffer
    }

    private fun getFortuneTelling() {
        try {
            showLoading(true)
            binding.resultTextView.text = "Fal yorumu alınıyor..."

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
                            Sen deneyimli bir kahve falı uzmanısın. 
                            Detaylı, olumlu ve umut verici yorumlar yaparsın. 
                            Her yorumunda aşk, kariyer ve sağlık konularına değinirsin.
                            Fincanın hem iç hem dış kısmını değerlendirirsin.
                        """.trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", """
                            Kahve fincanının hem iç hem dış kısmındaki telveleri görüyorum. 
                            Lütfen fincanın her iki yönünü de değerlendirerek detaylı ve 
                            olumlu bir fal yorumu yapar mısın?
                        """.trimIndent())
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 1000)
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful && responseBody != null) {
                            val fortune = JSONObject(responseBody)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")

                            showLoading(false)
                            binding.resultTextView.text = fortune
                        } else {
                            throw Exception("API yanıt vermedi: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        showError("Fal yorumu alınamadı: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            showLoading(false)
            showError("Fal yorumu başlatılamadı: ${e.message}")
        }
    }

    private fun checkResults() {
        if (isFirstImageTelveli && isSecondImageTelveli) {
            getFortuneTelling()
        } else {
            showLoading(false)
            val message = buildString {
                append("Fotoğraflarda yeterli telve tespit edilemedi.\n\n")
                append("İç kısım: ${if (isFirstImageTelveli) "Telveli ✓" else "Telvesiz ✗"}\n")
                append("Dış kısım: ${if (isSecondImageTelveli) "Telveli ✓" else "Telvesiz ✗"}\n\n")
                append("Lütfen fincanın iç ve dış kısmında telve olan fotoğraflar çekin.")
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
                Log.e(TAG, "Kamera başlatılamadı: ${e.message}", e)
                showError("Kamera başlatılamadı: ${e.message}")
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
                showError("Kamera izni olmadan uygulama çalışamaz")
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
}