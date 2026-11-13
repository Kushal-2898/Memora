package app.memora

import app.memora.data.local.BarcodeMetadata
import app.memora.data.local.OfflineProductCatalog
import app.memora.domain.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineProductCatalogTest {

    private val catalog = OfflineProductCatalog()

    @Test
    fun lookupKnownBarcode_returnsInstantOfflineMetadata() {
        // EAN-13 for Quaker Rolled Oats
        val match = catalog.lookup("8901499008236")

        assertNotNull(match)
        assertTrue(match?.productName?.contains("Oats", ignoreCase = true) == true)
        assertEquals(EventType.EXPIRATION, match?.category)
        assertTrue((match?.defaultShelfLifeMonths ?: 0) > 0)
    }

    @Test
    fun lookupGS1Prefix_identifiesCountryAndGeneralCategory() {
        // Barcode starting with 890 (India GS1 prefix)
        val match = catalog.lookup("8909999999999")

        assertNotNull(match)
        assertEquals("India", match?.originCountry)
    }

    @Test
    fun localUserCache_remembersUserEditedProductAcrossScans() {
        val customBarcode = "1234567890128"
        catalog.cacheProduct(
            customBarcode,
            BarcodeMetadata(
                productName = "Cold Pressed Olive Oil",
                brand = "Borges",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 24
            )
        )

        val retrieved = catalog.lookup(customBarcode)
        assertNotNull(retrieved)
        assertEquals("Cold Pressed Olive Oil", retrieved?.productName)
        assertEquals("Borges", retrieved?.brand)
        assertEquals(24, retrieved?.defaultShelfLifeMonths)
    }
}
