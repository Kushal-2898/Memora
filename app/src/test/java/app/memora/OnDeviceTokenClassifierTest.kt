package app.memora

import app.memora.platform.nlp.EntityTag
import app.memora.platform.nlp.OnDeviceTokenClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class OnDeviceTokenClassifierTest {

    private val classifier = OnDeviceTokenClassifier()

    @Test
    fun classifyUtilityBillTokens_correctlyTagsTitleDueDateAmountAndNoise() {
        val lines = listOf(
            "Torrent Power Electricity Bill",
            "Consumer No: 9876543210",
            "Bill Date: 01/10/2026",
            "Due Date: 15/10/2026",
            "Amount Payable: Rs 1450.00"
        )

        val classification = classifier.classifyDocument(lines)

        assertEquals("Torrent Power Electricity Bill", classification.inferredTitle)
        assertEquals("15/10/2026", classification.inferredDueDate)
        assertEquals("1450.00", classification.inferredAmount)
        assertTrue(classification.discardedNoise.contains("9876543210"))
    }

    @Test
    fun classifyPackagingTokens_correctlyTagsTitleExpiryAndBatchNoise() {
        val lines = listOf(
            "Organic Whole Wheat Bread",
            "Net Wt 400g",
            "Batch No: B-90124",
            "EXP: 25/11/2026"
        )

        val classification = classifier.classifyDocument(lines)

        assertEquals("Organic Whole Wheat Bread", classification.inferredTitle)
        assertEquals("25/11/2026", classification.inferredExpiryDate)
        assertTrue(classification.discardedNoise.contains("B-90124"))
    }

    @Test
    fun tokenClassification_runsUnder30msBudget() {
        val lines = listOf(
            "Apollo Pharmacy Invoice",
            "Rx: Amoxicillin 500mg",
            "Batch: AMX-882",
            "Mfg Date: 01/01/2026",
            "Use Before: 31/12/2027",
            "Total Amount: Rs 340.50"
        )

        // Warm up
        classifier.classifyDocument(lines)

        val duration = measureTimeMillis {
            repeat(10) {
                classifier.classifyDocument(lines)
            }
        } / 10

        assertTrue("Execution took ${duration}ms, expected < 30ms", duration < 30)
    }
}
