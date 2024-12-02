package com.app.qrcodescanner

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Size
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.qrcodescanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                binding.tvResult.text = "Camera permission is required"
            }
        }
        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val screenSize = Size(1280, 720)
        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(screenSize, ResolutionStrategy.FALLBACK_RULE_NONE)
        ).build()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImageProxy(imageProxy)
                    })
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("SetTextI18n")
    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        handleBarcode(barcode)
                    }
                }
                .addOnFailureListener {
                    binding.tvResult.text = "Failed to Scan QR Code!!!"
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }

        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleBarcode(barcode: Barcode) {
        val url = barcode.url?.url ?: barcode.displayValue
        if (url != null) {
            binding.tvResult.text = url
            binding.tvResult.setOnClickListener {
                startActivity(
                    Intent(applicationContext, WebViewActivity::class.java)
                        .putExtra("url", url)
                )
            }
        } else {
            binding.tvResult.text = "No QR Code detected"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}