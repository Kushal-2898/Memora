package app.memora.domain.extraction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.memora.domain.model.EventType
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class GeminiMemoryExtractor(
    private val context: Context,
    apiKey: String
) : MemoryExtractor {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = com.google.ai.client.generativeai.type.Schema(
                name = "MemoryExtraction",
                description = "Extracted memory candidate from a scanned document or product",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "title" to com.google.ai.client.generativeai.type.Schema(
                        name = "title", description = "Product or document title",
                        type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true,
                    ),
                    "eventType" to com.google.ai.client.generativeai.type.Schema(
                        name = "eventType", description = "One of: DUE_DATE, EXPIRATION, BEST_BEFORE, WARRANTY_END, RETURN_DEADLINE, SUBSCRIPTION_END, OTHER",
                        type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = false,
                    ),
                    "date" to com.google.ai.client.generativeai.type.Schema(
                        name = "date", description = "Key date in YYYY-MM-DD format",
                        type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true,
                    ),
                    "confidence" to com.google.ai.client.generativeai.type.Schema(
                        name = "confidence", description = "Confidence score 0.0 to 1.0",
                        type = com.google.ai.client.generativeai.type.FunctionType.NUMBER, nullable = false,
                    ),
                    "alternatives" to com.google.ai.client.generativeai.type.Schema(
                        name = "alternatives", description = "Alternative date candidates",
                        type = com.google.ai.client.generativeai.type.FunctionType.ARRAY,
                        items = com.google.ai.client.generativeai.type.Schema(
                            name = "alternative", description = "An alternative date candidate",
                            type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                            properties = mapOf(
                                "date" to com.google.ai.client.generativeai.type.Schema(
                                    name = "date", description = "Date in YYYY-MM-DD format",
                                    type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = false,
                                ),
                                "context" to com.google.ai.client.generativeai.type.Schema(
                                    name = "context", description = "Why this date is a candidate",
                                    type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = false,
                                ),
                            ),
                        ),
                    ),
                ),
                required = listOf("eventType", "confidence", "alternatives"),
            )
        },
        systemInstruction = content {
            text("""You are an expert AI scanner that extracts actionable expiration dates, bill due dates, warranty deadlines, and subscription renewals from images of physical products, medicine bottles/boxes, grocery packaging, receipts, utility bills, and documents.

CATEGORIES & NAMING GUIDELINES:
1. UTILITY & HOUSEHOLD BILLS:
   - Identify provider/biller name and service:
     * Gas: Indane, Bharat Gas, HP Gas, Adani Total Gas, Mahanagar Gas (MGL), IGL, Torrent Gas, GAIL, SCM units, PNG/LPG -> Title e.g. "Adani Gas Bill", "Indane LPG Bill".
     * Electricity: Bescom, Tata Power, BSES, MSEDCL, TNEB, UPPCL, Adani Electricity, Discom, kWh units -> Title e.g. "Bescom Electricity Bill", "Tata Power Bill".
     * Water: Municipal water, BWSSB, DJB, Jal Board, kL units -> Title e.g. "Water Bill".
     * Telecom & Internet: Airtel, Jio, BSNL, Vi, ACT Fibernet, Tata Play -> Title e.g. "Airtel Broadband Bill".
     * Financial: Credit cards (HDFC, SBI, ICICI, Axis, Amex), Rent, Loan EMI -> Title e.g. "HDFC Credit Card Due".
   - Set eventType to "DUE_DATE".
   - CRITICAL: Extract the PAYMENT DUE DATE ("DUE DATE", "PAY BY", "PAYMENT DUE", "DUE BEFORE", "LAST DATE FOR PAYMENT").
   - NEVER pick historical dates like "Bill Date", "Invoice Date", "Issue Date", "Bill Period", or "Meter Reading Date" as the primary date. Put those in 'alternatives'.

2. MEDICINES & HEALTHCARE:
   - Identify medicine blister strips, bottles, syrups, ointments, eye drops, prescription items.
   - Extract the specific drug/brand name and dosage (e.g. "Paracetamol 650mg Expiry", "Cough Syrup Expiry", "Amoxicillin 500mg Expiry").
   - Set eventType to "EXPIRATION".
   - CRITICAL: Distinguish from "MFG", "PKD", or batch dates. Never select manufacture dates as the primary date.

3. GROCERIES, DAIRY & FMCG:
   - Identify milk packets (Amul, Nandini, Mother Dairy, Country Delight), yogurt/curd, bread, butter, cheese, packaged foods.
   - Title e.g. "Amul Toned Milk Expiry", "Whole Wheat Bread Best Before".
   - Set eventType to "BEST_BEFORE" or "EXPIRATION".
   - Target "USE BY", "BEST BEFORE", "EXP". Ignore "PKD" or "PACKED" date.

4. COSMETICS & PERSONAL CARE:
   - Sunscreens, face wash, lotions, skincare, PAO codes -> Title e.g. "Sunscreen SPF 50 Expiry", eventType: "EXPIRATION".

5. WARRANTIES, SUBSCRIPTIONS & RETURNS:
   - Electronics/appliances: Laptops, phones, watches -> Title e.g. "MacBook Warranty", eventType: "WARRANTY_END".
   - Subscriptions & Insurance: Car/bike/health insurance (HDFC ERGO, ICICI Lombard, Star Health), Gym (Cult.fit, Gold's) -> Title e.g. "HDFC Car Insurance Renewal", "Cult.fit Gym Renewal", eventType: "SUBSCRIPTION_END".
   - Official documents: Passports, Driving Licenses -> Title e.g. "Passport Expiry", eventType: "EXPIRATION".
   - Returns: Retail store receipts (Zara, Amazon, Flipkart) -> Title e.g. "Zara Return Deadline", eventType: "RETURN_DEADLINE".

Return ONLY a valid JSON object matching this schema:
{ 
  "title": "A concise, specific descriptive title (e.g., 'Adani Gas Bill', 'Bescom Electricity Bill', 'Paracetamol 650mg Expiry')", 
  "eventType": "One of: DUE_DATE, EXPIRATION, BEST_BEFORE, WARRANTY_END, RETURN_DEADLINE, SUBSCRIPTION_END, OTHER", 
  "date": "The key future expiration or deadline date in YYYY-MM-DD format (or null if not found)", 
  "confidence": 0.0 to 1.0,
  "alternatives": [
    { "date": "YYYY-MM-DD", "context": "Reason why this date is a candidate, e.g. 'Bill Date' or 'Manufacture Date'" }
  ]
}
If no document, product, or date can be recognized, return title as null, date as null, and confidence as 0.0.""")
        }
    )

    override suspend fun extractCandidate(rawText: String, imageUri: Uri?): Result<ExtractedMemoryCandidate> =
        extractCandidate(rawText, imageUri, null)

    override suspend fun extractCandidate(rawText: String, imageUri: Uri?, scanId: String?): Result<ExtractedMemoryCandidate> = withContext(Dispatchers.IO) {
        val id = scanId ?: "N/A"
        val startTime = System.currentTimeMillis()
        try {
            app.memora.platform.logging.AppLog.checkMainThreadViolation("Gemini")
            val net = app.memora.platform.logging.AppLog.getNetworkSnapshot(context)
            app.memora.platform.logging.AppLog.recordGeminiRequest(id, model = "gemini-3.5-flash", dispatcherLabel = "IO")
            app.memora.platform.logging.AppLog.i("GEMINI", "[#$id] Network state: connected=${net.isConnected} | transport=${net.transportType} | metered=${net.isMetered}")
            val bitmap = imageUri?.let { loadBitmap(it) }
            if (bitmap != null) {
                app.memora.platform.logging.AppLog.recordImageMemory(bitmap.width, bitmap.height, bitmap.config?.name ?: "ARGB_8888", scanId = id)
            }
            val today = LocalDate.now()

            val prompt = content {
                if (bitmap != null) {
                    image(bitmap)
                }
                text("""
Today's date is: $today.

Analyze the image and the OCR text below:
$rawText

CRITICAL EXTRACTION RULES:
1. Identify the subject: What is this document or product? Identify the brand/company (e.g. Indane, Bescom, Amul, Cipla, HDFC) and what type of item it is.
2. For utility/household bills (Gas, Electricity, Water, Internet, Phone, Credit Card):
   - Set eventType = "DUE_DATE".
   - Target the PAYMENT DEADLINE: "PAYMENT DUE DATE", "DUE DATE", "PAY BY", "LAST DATE FOR PAYMENT", "DUE BEFORE".
   - NEVER select historical dates (Bill Date, Invoice Date, Issue Date, Meter Reading Date, Bill Period) as the primary date! Those are in the past. If present, place them in 'alternatives'.
3. For physical products (medicines, groceries, dairy, cosmetics):
   - Set eventType = "EXPIRATION" or "BEST_BEFORE".
   - Target "EXP", "EXPIRY", "USE BY", "BEST BEFORE", "BB", "BBD".
   - NEVER select "MFG" or "PKD" (manufacture/packing) dates as the primary date. Place those in 'alternatives'.
4. Date Priority against Today ($today):
   - Prioritize an actionable date that is on or after $today.
   - If multiple dates are found, set 'date' to the actionable future due/expiry date, and list other candidate dates in 'alternatives'.
   - If ALL dates on the document are in the past, return the date as 'date', and indicate in alternatives why it is a past date.
5. If the image is blurry, blank, or does not contain a recognizable document/product or date, return title: null, date: null, confidence: 0.0.
                """.trimIndent())
            }

            val response = kotlinx.coroutines.withTimeout(10_000L) {
                generativeModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty response from Gemini")
            val duration = System.currentTimeMillis() - startTime
            
            // Extract pure JSON object even if enclosed in markdown code blocks or surrounding text
            val firstBrace = jsonText.indexOf('{')
            val lastBrace = jsonText.lastIndexOf('}')
            val cleanJson = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                jsonText.substring(firstBrace, lastBrace + 1)
            } else {
                jsonText.trim()
            }

            app.memora.platform.logging.AppLog.recordGeminiSuccess(id, duration, cleanJson, dispatcherLabel = "IO")
            val jsonObject = JSONObject(cleanJson)

            val title = jsonObject.optString("title").takeIf { it.isNotBlank() && it != "null" }
            val eventTypeStr = jsonObject.optString("eventType", "OTHER")
            val eventType = try {
                EventType.valueOf(eventTypeStr)
            } catch (e: Exception) {
                EventType.OTHER
            }
            
            val dateStr = jsonObject.optString("date")
            val date = if (dateStr.isNotBlank() && dateStr != "null") {
                try {
                    LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            val confidence = jsonObject.optDouble("confidence", 0.8).toFloat()
            
            val alternativesArray = jsonObject.optJSONArray("alternatives")
            val alternatives = mutableListOf<ExtractedDateCandidate>()
            val allCandidates = mutableListOf<String>()
            if (date != null) {
                allCandidates.add("$date (Primary)")
            }

            if (alternativesArray != null) {
                for (i in 0 until alternativesArray.length()) {
                    val altObj = alternativesArray.optJSONObject(i) ?: continue
                    val altDateStr = altObj.optString("date")
                    val altContext = altObj.optString("context", "Candidate date")
                    if (altDateStr.isNotBlank() && altDateStr != "null") {
                        try {
                            val altDate = LocalDate.parse(altDateStr)
                            alternatives.add(ExtractedDateCandidate(
                                eventType = eventType,
                                eventDate = altDate,
                                confidence = confidence,
                                context = altContext,
                                hasExplicitDay = true
                            ))
                            allCandidates.add("$altDate ($altContext)")
                        } catch (e: Exception) {
                            // Ignore invalid alternative date formats
                        }
                    }
                }
            }

            var primaryDate = date
            var selectionReason = when {
                eventType == EventType.DUE_DATE -> "DUE_DATE priority (Payment deadline selected)"
                eventType == EventType.EXPIRATION || eventType == EventType.BEST_BEFORE -> "EXP/BEST_BEFORE selected, MFG date ignored"
                primaryDate != null -> "Actionable candidate selected"
                else -> "No actionable date found in extraction"
            }

            // Safeguard: If the primary date is in the past, but one of the alternatives is in the future,
            // automatically promote the future date to primary!
            if (primaryDate != null && primaryDate.isBefore(today)) {
                val futureAlternative = alternatives.firstOrNull { it.eventDate != null && !it.eventDate.isBefore(today) }
                if (futureAlternative != null && futureAlternative.eventDate != null) {
                    val oldPastDate = primaryDate
                    primaryDate = futureAlternative.eventDate
                    val altIndex = alternatives.indexOf(futureAlternative)
                    if (altIndex != -1) {
                        alternatives[altIndex] = futureAlternative.copy(
                            eventDate = oldPastDate,
                            context = if (eventType == EventType.DUE_DATE) "Bill generation date" else "Manufacture date"
                        )
                    }
                    selectionReason = "Primary date was past ($oldPastDate); future alternative ($primaryDate) promoted"
                }
            }

            app.memora.platform.logging.AppLog.recordCandidateParsed(
                scanId = id,
                title = title,
                date = primaryDate?.toString(),
                eventType = eventType.name,
                confidence = confidence,
                alternatives = alternatives.map { "${it.eventDate} (${it.context})" }
            )

            app.memora.platform.logging.AppLog.recordDateSelection(
                scanId = id,
                candidates = allCandidates,
                selectedDate = primaryDate?.toString(),
                reason = selectionReason
            )

            Result.success(
                ExtractedMemoryCandidate(
                    suggestedTitle = title,
                    suggestedEventType = eventType,
                    suggestedEventDate = primaryDate,
                    confidence = confidence,
                    rawTextSnippet = null,
                    requiresConfirmation = true,
                    alternatives = alternatives
                )
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val failureReason = app.memora.platform.logging.GeminiFailureReason.fromThrowable(e)
            val errorCategory = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(e)
            val errorDetail = "${e.javaClass.simpleName} ($errorCategory): ${app.memora.platform.logging.AppLog.sanitize(e.message ?: "")}"
            app.memora.platform.logging.AppLog.recordGeminiFailure(id, duration, failureReason, errorDetail, dispatcherLabel = "IO", throwable = e)
            Result.failure(e)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            // First decode bounds only to calculate sample size
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }

            val maxDimension = 1536
            var sampleSize = 1
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDimension && (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // Decode downsampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null

            // Read EXIF orientation and apply rotation so the bitmap is right-side-up.
            // Many Android cameras (Motorola, Samsung, Xiaomi) encode portrait photos
            // in landscape pixels with rotation stored only in EXIF metadata.
            val rotatedBitmap = try {
                val exifOrientation = context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    androidx.exifinterface.media.ExifInterface(exifStream)
                        .getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                        )
                } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

                val rotationDegrees = when (exifOrientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotationDegrees != 0f) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
                    val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (corrected !== bitmap) bitmap.recycle()
                    corrected
                } else {
                    bitmap
                }
            } catch (_: Exception) {
                bitmap // If EXIF reading fails, return the unrotated bitmap
            }

            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
