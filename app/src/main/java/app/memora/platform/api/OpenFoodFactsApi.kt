package app.memora.platform.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight, zero-dependency client for the Open Food Facts API (v0).
 * Fetches product titles and brands based on raw barcode digits.
 */
object OpenFoodFactsApi {
    private val STATUS_ONE_REGEX = Regex(""""status"\s*:\s*1\b""")
    private val PRODUCT_NAME_REGEX = Regex(""""product_name"\s*:\s*"([^"]+)"""")
    private val BRANDS_REGEX = Regex(""""brands"\s*:\s*"([^"]+)"""")

    suspend fun lookupBarcode(barcode: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanBarcode = barcode.replace(Regex("\\D"), "")
            if (cleanBarcode.isBlank()) return@withContext null

            val url = URL("https://world.openfoodfacts.org/api/v0/product/$cleanBarcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            connection.setRequestProperty("User-Agent", "Memora-Scanner-App/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                if (STATUS_ONE_REGEX.containsMatchIn(response)) {
                    val name = PRODUCT_NAME_REGEX.find(response)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                    val brand = BRANDS_REGEX.find(response)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                    return@withContext when {
                        name != null && brand != null && !name.startsWith(brand, ignoreCase = true) -> "$brand $name"
                        name != null -> name
                        else -> null
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
