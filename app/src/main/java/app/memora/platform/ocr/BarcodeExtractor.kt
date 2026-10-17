package app.memora.platform.ocr

/**
 * Platform abstraction for on-device Barcode extraction.
 */
interface BarcodeExtractor {
    /**
     * Extracts a barcode (if any) from the given image URI.
     * Returns the raw barcode string value.
     */
    suspend fun extractBarcode(imageUri: String): Result<String?>
}
