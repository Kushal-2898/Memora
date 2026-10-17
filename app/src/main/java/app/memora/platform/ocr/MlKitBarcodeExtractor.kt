package app.memora.platform.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitBarcodeExtractor(
    private val context: Context,
) : BarcodeExtractor {

    override suspend fun extractBarcode(imageUri: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val file = File(imageUri)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Image file not found: $imageUri"))
            }

            val inputImage = InputImage.fromFilePath(context, Uri.fromFile(file))
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .build()
            
            val scanner = BarcodeScanning.getClient(options)

            val barcode = suspendCancellableCoroutine<String?> { continuation ->
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstOrNull()?.rawValue
                        if (continuation.isActive) {
                            continuation.resume(value)
                        }
                    }
                    .addOnFailureListener { e ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                    .addOnCompleteListener {
                        // scanner.close() // Reusing the client is usually better, but MLKit manages it
                    }
            }

            Result.success(barcode)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
