package app.memora.data.local

import app.memora.domain.model.EventType
import java.util.concurrent.ConcurrentHashMap

data class BarcodeMetadata(
    val productName: String?,
    val brand: String? = null,
    val category: EventType = EventType.EXPIRATION,
    val defaultShelfLifeMonths: Int? = null,
    val originCountry: String? = null,
)

/**
 * High-performance, offline-first product and GTIN barcode catalog.
 *
 * Provides sub-millisecond local barcode resolution:
 * 1. User Local Cache: Remembers products scanned or edited on this device.
 * 2. Embedded GTIN Catalog: Common FMCG groceries, medicines, and household essentials.
 * 3. GS1 Global Prefix Resolver: Identifies product origin country and default category without network access.
 */
class OfflineProductCatalog(
    private val prefs: android.content.SharedPreferences? = null,
) {

    private val userCache = ConcurrentHashMap<String, BarcodeMetadata>()

    init {
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith("barcode_") && value is String) {
                val barcode = key.removePrefix("barcode_")
                val parts = value.split("|", limit = 2)
                if (parts.size == 2) {
                    val cat = runCatching { EventType.valueOf(parts[0]) }.getOrDefault(EventType.EXPIRATION)
                    userCache[barcode] = BarcodeMetadata(productName = parts[1], category = cat)
                }
            }
        }
    }

    companion object {
        private val STATIC_GTIN_CATALOG = mapOf(
            "8901499008236" to BarcodeMetadata(
                productName = "Quaker Rolled Oats 500g",
                brand = "Quaker",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12,
                originCountry = "India"
            ),
            "8901262010054" to BarcodeMetadata(
                productName = "Amul Taaza Homogenised Toned Milk",
                brand = "Amul",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 6,
                originCountry = "India"
            ),
            "8901030928451" to BarcodeMetadata(
                productName = "Bru Instant Coffee",
                brand = "Bru",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 18,
                originCountry = "India"
            ),
            "8901063012214" to BarcodeMetadata(
                productName = "Britannia Good Day Butter Cookies",
                brand = "Britannia",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 6,
                originCountry = "India"
            ),
            "8901725181222" to BarcodeMetadata(
                productName = "Dolo 650 Tablets",
                brand = "Micro Labs",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 24,
                originCountry = "India"
            ),
            "5000159461122" to BarcodeMetadata(
                productName = "Snickers Chocolate Bar",
                brand = "Mars",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12,
                originCountry = "United Kingdom"
            ),
            "013000006408" to BarcodeMetadata(
                productName = "Heinz Tomato Ketchup 20oz",
                brand = "Heinz",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12,
                originCountry = "United States / Canada"
            ),
            "016000275270" to BarcodeMetadata(
                productName = "General Mills Cheerios Cereal",
                brand = "Cheerios",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12,
                originCountry = "United States / Canada"
            ),
            "300450449107" to BarcodeMetadata(
                productName = "Tylenol Extra Strength 500mg Acetaminophen",
                brand = "Tylenol",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 24,
                originCountry = "United States / Canada"
            ),
            "051000012517" to BarcodeMetadata(
                productName = "Campbell's Condensed Tomato Soup",
                brand = "Campbell's",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 24,
                originCountry = "United States / Canada"
            ),
            "096619172288" to BarcodeMetadata(
                productName = "Kirkland Signature Organic Peanut Butter",
                brand = "Kirkland Signature",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12,
                originCountry = "United States / Canada"
            ),
            "742365264426" to BarcodeMetadata(
                productName = "Horizon Organic Whole Milk",
                brand = "Horizon Organic",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 2,
                originCountry = "United States / Canada"
            )
        )

        private val GS1_PREFIX_MAP = mapOf(
            "890" to ("India" to EventType.EXPIRATION),
            "00" to ("United States / Canada" to EventType.EXPIRATION),
            "01" to ("United States / Canada" to EventType.EXPIRATION),
            "02" to ("United States / Canada" to EventType.EXPIRATION),
            "03" to ("United States / Canada" to EventType.EXPIRATION),
            "04" to ("United States / Canada" to EventType.EXPIRATION),
            "05" to ("United States / Canada" to EventType.EXPIRATION),
            "06" to ("United States / Canada" to EventType.EXPIRATION),
            "07" to ("United States / Canada" to EventType.EXPIRATION),
            "08" to ("United States / Canada" to EventType.EXPIRATION),
            "09" to ("United States / Canada" to EventType.EXPIRATION),
            "10" to ("United States / Canada" to EventType.EXPIRATION),
            "11" to ("United States / Canada" to EventType.EXPIRATION),
            "12" to ("United States / Canada" to EventType.EXPIRATION),
            "13" to ("United States / Canada" to EventType.EXPIRATION),
            "30" to ("France" to EventType.EXPIRATION),
            "40" to ("Germany" to EventType.EXPIRATION),
            "49" to ("Japan" to EventType.EXPIRATION),
            "50" to ("United Kingdom" to EventType.EXPIRATION),
            "880" to ("South Korea" to EventType.EXPIRATION),
            "885" to ("Thailand" to EventType.EXPIRATION),
            "888" to ("Singapore" to EventType.EXPIRATION),
            "899" to ("Indonesia" to EventType.EXPIRATION),
        )
    }

    /**
     * Caches a user-confirmed product mapping locally.
     */
    fun cacheProduct(barcode: String, metadata: BarcodeMetadata) {
        val clean = barcode.trim()
        if (clean.isNotBlank()) {
            userCache[clean] = metadata
            val name = metadata.productName
            if (!name.isNullOrBlank()) {
                prefs?.edit()?.putString("barcode_$clean", "${metadata.category.name}|$name")?.apply()
            }
        }
    }

    /**
     * Resolves metadata for an EAN-13, EAN-8, or UPC-A barcode offline.
     */
    fun lookup(barcode: String): BarcodeMetadata? {
        val clean = barcode.trim()
        if (clean.isBlank()) return null

        // 1. Check user cache
        userCache[clean]?.let { return it }

        // 2. Check embedded catalog
        STATIC_GTIN_CATALOG[clean]?.let { return it }

        // 3. Fallback to GS1 Prefix Resolver
        for ((prefix, info) in GS1_PREFIX_MAP) {
            if (clean.startsWith(prefix)) {
                return BarcodeMetadata(
                    productName = null,
                    originCountry = info.first,
                    category = info.second
                )
            }
        }

        return null
    }
}
