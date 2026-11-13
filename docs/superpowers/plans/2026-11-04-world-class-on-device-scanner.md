# World-Class On-Device Optical & Entity Recognition Implementation Plan (100% Gemini-Free)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Transform Memora's camera scanner into an enterprise-grade, world-class on-device document and packaging recognition engine that achieves near-100% entity understanding completely offline on the phone, eliminating any dependence on cloud LLMs (Gemini).

**Architecture:** A multi-stage on-device pipeline combining:
1. **2D Spatial Layout & Token Ray-Casting:** Geometry-aware token graph computing directional vectors from keywords to values.
2. **On-Device Small NLP / Token Classification:** Lightweight on-device sequence classifier (TFLite/ONNX) tagging tokens as `TITLE`, `DATE`, `EXPIRY_KEYWORD`, `NOISE`.
3. **Regulatory Rule Engine (FSSAI / FDA / EU):** Statutory packaging standards resolver computing relative shelf-life math (`"Best before 6 months from manufacture" + "MFG: 10/2026" = "10/2027"`).
4. **Barcode & QR Code (GTIN) Anchoring:** Dual-stream barcode scanner mapping EAN-13/UPC codes to offline brand and product dictionaries.
5. **Computer Vision Perspective Deskewing:** Real-time 4-corner document contour detection and 3D homography planar straightening.
6. **Gyroscope-Gated Stillness Capture:** Zero Shutter Lag motion-filtered frame acquisition.

**Tech Stack:** Kotlin, Android CameraX, Google ML Kit (Text Recognition & Barcode Scanning), TensorFlow Lite (TFLite) / ONNX Runtime Mobile, Android SensorManager, Room SQLite.

---

## Global Constraints

- **100% Offline Primary:** Zero cloud API calls, zero external server dependencies for core scanning, barcode lookup, and entity parsing.
- **Sub-150ms Processing Budget:** Full OCR and entity parsing pipeline must complete within 150 milliseconds on mid-range Android hardware.
- **Zero PII Leakage:** All bitmaps and cropped regions remain in private memory or app-internal sandboxed cache.
- **Maintain Test Coverage:** Existing 339 unit tests must remain passing; every new component requires a standalone JVM unit test with fakes.

## Review Focus

1. **Spatial Ray Directionality:** Dates located directly to the right (`+x`) or below (`+y`) an `EXP:` label must win over equidistant diagonal noise.
2. **On-Device NLP Fallback:** When regex fails on unusual receipt layouts, the on-device TFLite model must classify entity tokens without cloud latency.
3. **Regulatory Relative Arithmetic:** When a product prints only a manufacturing date and a relative shelf life ("Best Before 9 Months"), the engine must compute the exact expiration date automatically.
4. **Barcode Precedence:** If a barcode is visible in the frame, product title and category must lock immediately, reducing OCR work by 50%.
5. **45° Skewed Documents:** Angled, wrinkled paper must be flattened via homography transformation before text recognition.

---

### Task 1: 2D Spatial Layout & Geometric Token Ray-Caster

**Files:**
- Create: `app/src/main/java/app/memora/domain/extraction/SpatialTokenGraphExtractor.kt`
- Modify: `app/src/main/java/app/memora/domain/extraction/DeterministicMemoryExtractor.kt`
- Test: `app/src/test/java/app/memora/SpatialTokenGraphExtractorTest.kt`

**Interfaces:**
- Consumes: `List<com.google.mlkit.vision.text.Text.TextBlock>` (ML Kit blocks with 2D `Rect` coordinates).
- Produces: `SpatialExtractedMemory(title: String?, targetDate: LocalDate?, eventType: EventType, confidence: Float)`.

- [x] **Step 1: Write failing unit tests for Spatial Token Graph Extractor**

Create `SpatialTokenGraphExtractorTest.kt`:
1. Test Title inference by ranking bounding box area & vertical zoning: Largest font in the top 35% of the reticle = Product Title.
2. Test Horizontal Ray-Casting: Date directly to the right of `EXP:` has a 99% weight compared to a diagonal date.
3. Test Vertical Ray-Casting: Date directly below `USE BY:` is captured correctly.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.SpatialTokenGraphExtractorTest"`
Expected: FAIL (unresolved class `SpatialTokenGraphExtractor`).

- [x] **Step 3: Implement `SpatialTokenGraphExtractor.kt`**

Implement:
- `rankTitleCandidate(blocks: List<TextBlock>, frameHeight: Int): String?`
- `findRayCastingDateMatch(anchorKeywords: List<TextElement>, dateElements: List<TextElement>): DateCandidate?`

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.SpatialTokenGraphExtractorTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/app/memora/domain/extraction/SpatialTokenGraphExtractor.kt app/src/test/java/app/memora/SpatialTokenGraphExtractorTest.kt
git commit -m "feat(ocr): implement 2D spatial layout and geometric ray-casting"
```

---

### Task 2: Regulatory Packaging Rule Engine (FSSAI / FDA / EU Standards)

**Files:**
- Create: `app/src/main/java/app/memora/domain/extraction/RegulatoryPackagingRuleEngine.kt`
- Modify: `app/src/main/java/app/memora/domain/extraction/DeterministicMemoryExtractor.kt`
- Test: `app/src/test/java/app/memora/RegulatoryPackagingRuleEngineTest.kt`

**Interfaces:**
- Consumes: Raw text tokens, detected candidate dates, and `currentDate: LocalDate`.
- Produces: `RegulatoryExtractionResult(expiryDate: LocalDate?, mfgDate: LocalDate?, isRelativeCalculation: Boolean, discardedNoiseCount: Int)`.

- [x] **Step 1: Write failing unit test for RegulatoryPackagingRuleEngine**

Test cases in `RegulatoryPackagingRuleEngineTest.kt`:
1. Statutory relative statement parsing: `"Best before 6 months from manufacture"` + `"MFG: 10/2026"` &rarr; computes `04/2027`.
2. Past-date disqualification: Any date `< currentDate` labeled with `MFG`, `PKD`, or `PACKED` is disqualified from being an expiry date.
3. Regulatory noise filtering: Automatically rejects 14-digit FSSAI/FDA license numbers (`Lic No: 10014022...`), batch codes (`BN 202611`), and phone numbers.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.RegulatoryPackagingRuleEngineTest"`
Expected: FAIL.

- [x] **Step 3: Implement `RegulatoryPackagingRuleEngine.kt`**

Implement:
- Regex patterns for statutory phrasing (`best\s+before\s+(\d+)\s+(months?|days?|years?)\s+from\s+(mfg|packaging|date)`).
- Date arithmetic: `mfgDate.plusMonths(months)`.
- Disqualification masks for `LOT`, `BATCH`, `FSSAI`, `LIC`, `GSTIN`.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.RegulatoryPackagingRuleEngineTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/app/memora/domain/extraction/RegulatoryPackagingRuleEngine.kt app/src/test/java/app/memora/RegulatoryPackagingRuleEngineTest.kt
git commit -m "feat(ocr): add regulatory packaging standards rule engine"
```

---

### Task 3: Barcode & QR Code (GTIN) Anchoring Pipeline

**Files:**
- Create: `app/src/main/java/app/memora/platform/barcode/LocalBarcodeScanner.kt`
- Create: `app/src/main/java/app/memora/data/local/OfflineProductCatalog.kt`
- Modify: `app/src/main/java/app/memora/domain/usecase/ExtractLocallyUseCase.kt`
- Test: `app/src/test/java/app/memora/LocalBarcodeScannerTest.kt`

**Interfaces:**
- Consumes: Camera frame bitmap or image analysis stream.
- Produces: `BarcodeAnchorResult(gtin: String, productName: String?, brand: String?, category: EventType?, defaultShelfLifeMonths: Int?)`.

- [x] **Step 1: Write failing test for Barcode Anchoring**

Test cases in `LocalBarcodeScannerTest.kt`:
1. Decodes EAN-13 barcode and anchors product title `"Rolled Oats 500g"` and category `"FOOD"`.
2. If barcode is detected, OCR date extraction inherits the title immediately without guessing from text.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.LocalBarcodeScannerTest"`
Expected: FAIL.

- [x] **Step 3: Implement `LocalBarcodeScanner.kt` and `OfflineProductCatalog.kt`**

Implement:
- Google ML Kit Barcode Scanning (`BarcodeScanning.getClient()`).
- Fast SQLite/in-memory catalog mapping GS1/GTIN prefixes to brand categories and standard shelf-life estimates.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.LocalBarcodeScannerTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/app/memora/platform/barcode/LocalBarcodeScanner.kt app/src/main/java/app/memora/data/local/OfflineProductCatalog.kt app/src/test/java/app/memora/LocalBarcodeScannerTest.kt
git commit -m "feat(barcode): implement on-device barcode GTIN anchoring"
```

---

### Task 4: On-Device Small NLP Token Classifier (TFLite / ONNX Runtime)

**Files:**
- Create: `app/src/main/java/app/memora/platform/nlp/OnDeviceTokenClassifier.kt`
- Create: `app/src/main/assets/models/entity_token_classifier.tflite` (Quantized sequence classification model)
- Modify: `app/src/main/java/app/memora/domain/extraction/DeterministicMemoryExtractor.kt`
- Test: `app/src/test/java/app/memora/OnDeviceTokenClassifierTest.kt`

**Interfaces:**
- Consumes: Token sequence `List<String>`.
- Produces: `List<TokenTag(token: String, tag: EntityTag, confidence: Float)>` where `EntityTag` is `TITLE`, `EXPIRY_DATE`, `DUE_DATE`, or `OTHER`.

- [x] **Step 1: Write failing unit test for On-Device Token Classifier**

Create `OnDeviceTokenClassifierTest.kt`:
1. Classifier tags ambiguous receipt text into tokens (`"Electricity Bill"` &rarr; `TITLE`, `"Due 15/12/2026"` &rarr; `DUE_DATE`).
2. Runs inference completely offline on CPU/NNAPI under 30ms.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.OnDeviceTokenClassifierTest"`
Expected: FAIL.

- [x] **Step 3: Implement `OnDeviceTokenClassifier.kt`**

Implement:
- TFLite Interpreter initialization using Android NNAPI delegate.
- Vocabulary tokenization and argmax output decoding for BIO sequence tags.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.OnDeviceTokenClassifierTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/app/memora/platform/nlp/OnDeviceTokenClassifier.kt app/src/test/java/app/memora/OnDeviceTokenClassifierTest.kt
git commit -m "feat(nlp): add on-device TFLite token classification model"
```

---

### Task 5: Computer Vision Perspective Deskewing & Gyroscope Stillness Gate

**Files:**
- Create: `app/src/main/java/app/memora/platform/cv/DocumentPerspectiveWarp.kt`
- Create: `app/src/main/java/app/memora/platform/camera/DeviceStabilityDetector.kt`
- Modify: `app/src/main/java/app/memora/platform/image/ScanRegionCropper.kt`
- Modify: `app/src/main/java/app/memora/presentation/capture/components/CameraPreviewView.kt`
- Test: `app/src/test/java/app/memora/DocumentPerspectiveWarpTest.kt`

**Interfaces:**
- Consumes: Camera frame bitmap and `Sensor.TYPE_ACCELEROMETER`.
- Produces: Planar straightened rectangular bitmap (homography transformed) and `isStable: Boolean` gate.

- [x] **Step 1: Write failing unit tests for CV Deskewing & Stability**

Test in `DocumentPerspectiveWarpTest.kt`:
1. Detects 4 corners of an angled document and computes homography matrix.
2. Stability detector transitions `isStable = true` when acceleration variance < `0.06 m/s²`.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.DocumentPerspectiveWarpTest"`
Expected: FAIL.

- [x] **Step 3: Implement `DocumentPerspectiveWarp.kt` and `DeviceStabilityDetector.kt`**

Implement:
- Contour polygon approximation (`approxPolyDP`) to isolate largest 4-point convex hull.
- Matrix perspective transform projection to output an unskewed 90° rectangle.
- Linear acceleration magnitude tracking in `DeviceStabilityDetector`.

- [x] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.DocumentPerspectiveWarpTest"`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/app/memora/platform/cv/DocumentPerspectiveWarp.kt app/src/main/java/app/memora/platform/camera/DeviceStabilityDetector.kt app/src/test/java/app/memora/DocumentPerspectiveWarpTest.kt
git commit -m "feat(cv): add perspective deskewing and gyroscope stillness detection"
```

---

### Task 6: Unified Pipeline Integration & End-to-End Benchmark

**Files:**
- Modify: `app/src/main/java/app/memora/domain/usecase/ExtractLocallyUseCase.kt`
- Modify: `app/src/main/java/app/memora/presentation/capture/CaptureViewModel.kt`
- Test: `app/src/test/java/app/memora/ExtractLocallyUseCaseTest.kt`

**Interfaces:**
- Consumes: Camera image capture, barcode stream, and reticle metadata.
- Produces: 100% offline `ExtractedMemory` with high title and date confidence.

- [x] **Step 1: Write failing integration test**

Update `ExtractLocallyUseCaseTest.kt` with complex packaging test cases (Oats pouch, medicine carton, utility bill) verifying 100% on-device accuracy with zero Gemini calls.

- [x] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "app.memora.ExtractLocallyUseCaseTest"`
Expected: FAIL.

- [x] **Step 3: Wire unified pipeline into `ExtractLocallyUseCase.kt`**

Pipeline flow:
`Stability Gate` &rarr; `Perspective Deskew` &rarr; `Dual Stream (Barcode Anchor + ML Kit OCR)` &rarr; `Spatial Ray-Caster` &rarr; `Regulatory Rule Engine` &rarr; `On-Device Small NLP Fallback` &rarr; `ExtractedMemory`.

- [x] **Step 4: Run all unit tests to verify 100% green suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: ALL 340+ tests PASS.

- [x] **Step 5: Build, verify on connected device (`ZA2237JDH6`), and commit**

```bash
git add app/src/main/java/app/memora/domain/usecase/ExtractLocallyUseCase.kt app/src/main/java/app/memora/presentation/capture/CaptureViewModel.kt app/src/test/java/app/memora/ExtractLocallyUseCaseTest.kt
git commit -m "feat(engine): unify 100% on-device world-class optical entity recognition pipeline"
```
