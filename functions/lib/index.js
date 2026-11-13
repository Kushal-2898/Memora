"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.extractMemoryWithGemini = void 0;
const https_1 = require("firebase-functions/v2/https");
const params_1 = require("firebase-functions/params");
const admin = require("firebase-admin");
const generative_ai_1 = require("@google/generative-ai");
admin.initializeApp();
const geminiApiKey = (0, params_1.defineSecret)("GEMINI_API_KEY");
exports.extractMemoryWithGemini = (0, https_1.onCall)({
    secrets: [geminiApiKey],
    timeoutSeconds: 60,
    memory: "512MiB",
}, async (request) => {
    // 1. Enforce authentication
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "You must be signed in to use Gemini AI extraction.");
    }
    const data = request.data;
    if (!data || (!data.prompt && !data.imageBase64)) {
        throw new https_1.HttpsError("invalid-argument", "Request must contain a prompt or imageBase64.");
    }
    const apiKey = geminiApiKey.value() || process.env.GEMINI_API_KEY;
    if (!apiKey) {
        throw new https_1.HttpsError("failed-precondition", "Server-side GEMINI_API_KEY is not configured in Secret Manager.");
    }
    const genAI = new generative_ai_1.GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
        model: "gemini-2.5-flash",
        generationConfig: {
            responseMimeType: "application/json",
            responseSchema: {
                type: generative_ai_1.SchemaType.OBJECT,
                description: "Extracted memory candidate from a scanned document or product",
                properties: {
                    title: {
                        type: generative_ai_1.SchemaType.STRING,
                        description: "Product or document title",
                        nullable: true,
                    },
                    eventType: {
                        type: generative_ai_1.SchemaType.STRING,
                        description: "One of: DUE_DATE, EXPIRATION, BEST_BEFORE, WARRANTY_END, RETURN_DEADLINE, SUBSCRIPTION_END, OTHER",
                    },
                    date: {
                        type: generative_ai_1.SchemaType.STRING,
                        description: "Key date in YYYY-MM-DD format (if only month/year is printed, format as YYYY-MM-01)",
                        nullable: true,
                    },
                    confidence: {
                        type: generative_ai_1.SchemaType.NUMBER,
                        description: "Confidence score 0.0 to 1.0",
                    },
                    alternatives: {
                        type: generative_ai_1.SchemaType.ARRAY,
                        description: "Alternative date candidates",
                        items: {
                            type: generative_ai_1.SchemaType.OBJECT,
                            properties: {
                                date: {
                                    type: generative_ai_1.SchemaType.STRING,
                                    description: "Date in YYYY-MM-DD format",
                                },
                                context: {
                                    type: generative_ai_1.SchemaType.STRING,
                                    description: "Why this date is a candidate",
                                },
                            },
                            required: ["date", "context"],
                        },
                    },
                },
                required: ["eventType", "confidence", "alternatives"],
            },
        },
        systemInstruction: `You are an expert AI scanner that extracts actionable expiration dates, bill due dates, warranty deadlines, and subscription renewals from images of physical products, medicine packaging, grocery items, receipts, utility bills, and documents.

CATEGORIES & NAMING GUIDELINES:
1. UTILITY & HOUSEHOLD BILLS:
   - Identify biller (Gas, Electricity, Water, Internet, Credit Card, Rent).
   - Set eventType to DUE_DATE.
   - Extract the PAYMENT DUE DATE ("DUE DATE", "PAY BY", "LAST DATE FOR PAYMENT").
   - NEVER select historical bill/invoice date as primary date.

2. MEDICINES & HEALTHCARE:
   - Extract drug/brand name and dosage (e.g. "Paracetamol 650mg Expiry").
   - Set eventType to EXPIRATION.
   - Ignore MFG/PKD date for primary date.

3. GROCERIES, DAIRY & FMCG:
   - Identify brand and item (e.g. "Amul Toned Milk Expiry").
   - Set eventType to BEST_BEFORE or EXPIRATION.
   - Target USE BY, BEST BEFORE, EXP.

4. COSMETICS & GENERAL:
   - Expiry or warranty deadlines. Look for debossed/embossed crimp dates on tubes.

RULES:
- Always format dates as YYYY-MM-DD.
- Return ONLY valid JSON adhering to the specified schema.`,
    });
    const parts = [];
    if (data.imageBase64) {
        parts.push({
            inlineData: {
                data: data.imageBase64,
                mimeType: data.mimeType || "image/jpeg",
            },
        });
    }
    if (data.prompt) {
        parts.push({ text: data.prompt });
    }
    try {
        const result = await model.generateContent(parts);
        const responseText = result.response.text();
        const parsed = JSON.parse(responseText);
        return {
            title: parsed.title || null,
            eventType: parsed.eventType || "OTHER",
            date: parsed.date || null,
            confidence: typeof parsed.confidence === "number" ? parsed.confidence : 0.8,
            alternatives: Array.isArray(parsed.alternatives) ? parsed.alternatives : [],
        };
    }
    catch (error) {
        console.error("Gemini extraction error:", error);
        throw new https_1.HttpsError("internal", error.message || "Failed to extract memory with Gemini");
    }
});
//# sourceMappingURL=index.js.map