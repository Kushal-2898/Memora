# Google Play Console — Data Safety Section Specification

**Application Name:** Memora  
**Application ID:** `app.memora`  
**Target Release:** 1.0.0 (Production Release)  
**Standard:** Google Play Data Safety Policy & India DPDP Act (2023)

Use the exact specifications below when completing the **Data safety questionnaire** in Google Play Console.

---

### Question 1: Data Collection & Sharing

1. **Does your app collect or share any of the required user data types?**
   * **Answer:** **No**
   * *Rationale:* All document scanning, OCR extraction, image storage, and reminder management are performed strictly offline on the user's physical device. No personal data is collected or transmitted to external servers.

2. **Is all of the user data collected by your app encrypted in transit?**
   * **Answer:** **Not applicable** (The app does not transmit user data across networks in standard operation).

3. **Do you provide a way for users to request that their data be deleted?**
   * **Answer:** **Yes**
   * *Mechanism:* Users can swipe to delete individual memories or tap **"Erase All Data"** in *Settings > Privacy & Data Protection* to wipe all local data instantly. Additionally, uninstalling the app permanently erases all sandboxed data.

---

### Question 2: Data Types Breakdown

| Data Type | Collected? | Shared? | Processing Details |
| :--- | :--- | :--- | :--- |
| **Name, Email, Phone number, User IDs** | ❌ No | ❌ No | No user account or profile system exists. |
| **Photos and Videos** | ❌ No | ❌ No | Photos captured via the camera stay exclusively in app-specific internal storage (`context.filesDir`). Never uploaded. |
| **Financial Info (Credit card, bank info)** | ❌ No | ❌ No | Not collected. |
| **Location (Precise / Approximate)** | ❌ No | ❌ No | No location permissions requested or accessed. |
| **Health & Fitness** | ❌ No | ❌ No | Not collected. |
| **Messages / SMS / Contacts** | ❌ No | ❌ No | Not collected. |
| **App Performance & Diagnostics** | ❌ No | ❌ No | No third-party crash reporting SDKs (Crashlytics, Sentry) transmitting telemetry over the wire. Diagnostic logs are stored locally on-device and only viewable by the user in Settings. |
| **Device or other IDs** | ❌ No | ❌ No | No advertising IDs or IMEI queried. |

---

### Question 3: Security Practices

* **Data is not collected or shared externally.**
* **Local Storage Encryption:** Data resides in Android's sandboxed, encrypted file system (file-based encryption on Android 7.0+).
* **Independent Security Review:** Not applicable for standard Tier 1 utilities, but architecture conforms to OWASP MASVS (Mobile Application Security Verification Standard) L1.

---

### Privacy Policy URL for Play Console Store Listing
* Provide the URL hosting `PRIVACY_POLICY.md` (e.g. `https://memora-app.github.io/privacy` or your personal GitHub raw link).
