# Memora — Privacy Policy & Data Protection Charter

**Effective Date:** October 16, 2026  
**Last Updated:** November 8, 2026  
**Compliance Standard:** India Digital Personal Data Protection Act, 2023 (DPDP Act, 2023) & Global Privacy-by-Design Standards

---

## 1. Executive Summary: Privacy-by-Design
Memora is built from the ground up as a **100% Local-First, Offline-Primary** application. We believe that your personal documents, receipts, bills, and expiry dates belong strictly to you. 

* **Guest Mode by Default:** You can use 100% of core features without creating an account or providing any personal identifiers (no passwords, phone numbers, or advertising identifiers).
* **Optional Account Linking:** If you choose to link a Google Account, Memora accesses only your basic profile information (display name, email address, avatar) solely to verify identity and manage optional cloud synchronization. We never sell, rent, or monetize your data.
* **100% On-Device OCR:** Optical Character Recognition (powered by Google ML Kit) executes directly on your device's processor (CPU/NPU). No images or text are sent to third-party cloud AI servers.
* **Zero Telemetry or Ad Tracking:** Memora contains no advertising identifiers, tracking beacons, or third-party behavioral analytics SDKs.
* **Local Sandboxed Storage & Encrypted Cloud:** All memories, images, and reminder alarms are stored strictly within your device's private, encrypted internal storage, with optional user-authorized cloud backup.

---

## 2. Information We Do NOT Collect
Under Section 6 of the DPDP Act (Principle of Data Minimisation), Memora strictly limits data processing:
* We do **not** collect Personally Identifiable Information (PII).
* We do **not** collect precise GPS location data.
* We do **not** collect device contacts, call logs, SMS, or external files.
* We do **not** upload captured photos or extracted dates to any continuous cloud server.

---

## 3. Data Processing & Technical Architecture

### A. Document Capture & Text Extraction
* When you capture an image of a receipt, document, or packaging, the image is processed in your device's private sandboxed memory.
* Text and candidate dates are recognized locally using embedded on-device OCR models (Google ML Kit).
* No network connection is required for scanning or reminder scheduling.

### B. Hybrid On-Device & Optional Cloud AI Refinement
* **Default Mode (100% On-Device):** Primary optical character recognition (OCR) and date extraction run completely on your device using Google ML Kit. Scanned photos never leave your device for standard recognition.
* **Optional Cloud AI:** For difficult, low-contrast, or multi-date packaging, Memora provides an optional "Analyze with AI" feature powered by Google Gemini.
* **Strict Double-Consent Guarantee:** Cloud AI is **NEVER** triggered automatically or silently in the background. It is invoked exclusively when you manually tap the *"✨ Analyze with AI"* button on the scan review bottom sheet. Images are transmitted securely over encrypted TLS, used solely to extract the dates, and are never retained, sold, or used to train third-party AI models.

### C. Alarms & Notifications
* Reminders are scheduled locally using the Android operating system's native `AlarmManager`.
* Reminders trigger local status bar notifications; no external push notification service (such as FCM) is used to track your reminder schedule.

---

## 4. Your Rights as a Data Principal (DPDP Act, 2023)
Under Sections 11, 12, 13, and 14 of the Digital Personal Data Protection Act, 2023, you hold the following statutory rights:

1. **Right to Access Information (Section 11):**  
   You have immediate, direct access to all data stored within the app via the Home and History screens.
2. **Right to Correction, Erasure & Account Deletion (Section 12):**  
   * You can edit any title, category, or reminder date at any time.
   * You can delete individual memories and associated image files with a simple swipe.
   * You can permanently purge all stored data via the **"Erase All Data"** button in *Settings > Privacy & Data Protection*.
   * If you connected an account, you can permanently delete your account and all associated cloud data via **"Delete Account"** in *Settings > Account*, or by emailing `klabsstudios@gmail.com` or `privacy@memora.app`.
3. **Right to Grievance Redressal (Section 13):**  
   You have the right to readily available means of grievance redressal regarding data protection.
4. **Right to Nominate (Section 14):**  
   You may designate an individual who, in the event of death or incapacity, shall exercise your rights in accordance with applicable law.

---

## 5. Device Permissions & Justification

| Permission | Technical Name | Purpose & Usage |
| :--- | :--- | :--- |
| **Camera** | `android.permission.CAMERA` | Used exclusively to capture photos of documents and expiry dates inside the viewfinder. Photos are never shared outside the app. |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Used to display local reminder notifications when an item expires or a bill is due. |
| **Alarm Scheduling** | `android.permission.SCHEDULE_EXACT_ALARM` | Ensures morning reminder notifications arrive promptly at 09:00 local time. |

---

## 6. Children's Privacy (Section 9)
Memora does not target, collect, or process personal data from children under 18 years of age.

---

## 7. Data Retention & Erasure Policy
* **Retention Period:** Data is retained on your device solely for as long as you keep the app installed or choose to keep the record.
* **Deletion:** Deleting a memory instantly purges the SQLite database entry and deletes the underlying JPEG file from disk.
* **Uninstallation:** Uninstalling the app or using Android's "Clear Storage" immediately and irreversibly destroys 100% of stored data.

---

## 8. Grievance Redressal & Contact Officer
In compliance with Section 13 of the DPDP Act, 2023 and the Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021, any privacy inquiries, feedback, or statutory grievance redressal requests may be directed to our designated Data Protection Officer:

* **Entity / Developer:** K-Labs Studios
* **Designation:** Data Protection & Grievance Officer
* **Official Email:** `klabsstudios@gmail.com`
* **Alternative Email:** `privacy@memora.app`
* **Response Window:** All inquiries are acknowledged within 48 hours and resolved within 30 days as mandated by statutory guidelines.

---

## 9. Terms of Use & Limitation of Liability

### A. Important Safety & Auxiliary Aid Notice
Memora is designed strictly as an auxiliary, personal organizational reminder aid. **It is NOT a medical device, Clinical Decision Support System (CDSS), or laboratory food safety testing tool.**

### B. Mandatory Physical Verification Duty
Optical Character Recognition (OCR) and automated date detection algorithms are subject to real-world optical noise, curved packaging distortion, faded ink, and varying lighting conditions.
* You retain sole responsibility for physically inspecting all food, beverage, medicine, cosmetics, and perishable items for freshness, spoilage, odor, physical defects, and physical manufacturer labels before consumption or administration.
* Never consume spoiled food or administer expired medicines regardless of any date recorded or displayed in the application.
* For medical and pharmaceutical products, always follow the guidance of a qualified healthcare professional and the official manufacturer package leaflet.

### C. Disclaimer of Warranties ("As-Is")
Memora is provided strictly on an **"AS-IS"** and **"AS-AVAILABLE"** basis without warranties of any kind, whether express, statutory, or implied, including but not limited to the accuracy of extracted dates, uninterrupted notification delivery, or fitness for a particular purpose.

### D. Limitation of Damages
To the maximum extent permitted by applicable law, K-Labs Studios, its developers, and affiliates shall not be liable for any direct, indirect, incidental, consequential, special, or exemplary damages, including but not limited to illness, foodborne disease, allergic reactions, personal injury, medical costs, spoiled groceries, missed due dates, or device battery management shutdowns resulting from reliance on the application.

### E. Governing Law & Jurisdiction
These terms, disclaimers, and data protection policies shall be governed by and construed in accordance with the **laws of India**. Any disputes arising in connection with the application shall be subject to the exclusive jurisdiction of the competent courts in India.
