package com.example.authentication.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * CameraX preview bound to the lifecycle, feeding frames into ML Kit Barcode Scanning.
 * Only emits the first successfully parsed otpauth:// URI via [onQrCodeScanned].
 */
@Composable
fun QrScannerView(
    onQrCodeScanned: (rawValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Prevent multiple callbacks from the same QR code
    var hasScanned by remember { mutableStateOf(false) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val executor = Executors.newSingleThreadExecutor()
                val barcodeScanner = BarcodeScanning.getClient()

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                if (!hasScanned) {
                                    @androidx.camera.core.ExperimentalGetImage
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull { barcode ->
                                                    barcode.format == Barcode.FORMAT_QR_CODE &&
                                                    barcode.rawValue?.startsWith("otpauth://") == true
                                                }?.rawValue?.let { raw ->
                                                    if (!hasScanned) {
                                                        hasScanned = true
                                                        onQrCodeScanned(raw)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )

        // Aiming overlay
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
        )

        Text(
            text = "Point at an otpauth:// QR code",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/**
 * Parses an otpauth://totp/ URI into its components.
 * Example: otpauth://totp/GitHub:alex@example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
 *
 * @return Triple(issuer, accountName, secret) or null if the URI is malformed.
 */
fun parseOtpAuthUri(uri: String): Triple<String, String, String>? {
    return try {
        val stripped = uri.removePrefix("otpauth://totp/")
        val queryStart = stripped.indexOf('?')
        if (queryStart == -1) return null

        val labelPart = stripped.substring(0, queryStart)
        val queryPart = stripped.substring(queryStart + 1)

        val params = queryPart.split("&").associate {
            val kv = it.split("=", limit = 2)
            kv[0] to kv.getOrElse(1) { "" }
        }

        val secret = params["secret"] ?: return null
        val issuer = params["issuer"]
            ?: labelPart.substringBefore(":").ifEmpty { labelPart }
        val accountName = labelPart.substringAfter(":").ifEmpty { labelPart }

        Triple(issuer, accountName, secret)
    } catch (e: Exception) {
        null
    }
}
