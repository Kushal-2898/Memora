package app.memora.platform.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight, zero-dependency client for the Open Food Facts API (v0).
 * Fetches product titles and brands based on raw barcode digits.
 */
object OpenFoodFactsApi {
    
    suspend fun lookupBarcode(barcode: String): String? = withContext(Dispatchers.IO) {
        try {
            // Strip any non-digit characters just in case
            val cleanBarcode = barcode.replace(Regex("\\D"), "")
            if (cleanBarcode.isBlank()) return@withContext null

            val url = URL("https://world.openfoodfacts.org/api/v0/product/$cleanBarcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Memora-Scanner-App/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                if (json.optInt("status") == 1) {
                    val product = json.optJSONObject("product")
                    val name = product?.optString("product_name")?.takeIf { it.isNotBlank() }
                    val brand = product?.optString("brands")?.takeIf { it.isNotBlank() }
                    
                    return@withContext when {
                        name != null && brand != null -> "$brand $name"
                        name != null -> name
                        else -> null
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
