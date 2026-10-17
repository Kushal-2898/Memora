# Memora — Privacy Policy & Data Protection Charter

**Effective Date:** October 16, 2026  
**Last Updated:** October 16, 2026  
**Compliance Standard:** India Digital Personal Data Protection Act, 2023 (DPDP Act, 2023) & Global Privacy-by-Design Standards

---

## 1. Executive Summary: Privacy-by-Design
Memora is built from the ground up as a **Local-First, Offline-Primary** application. We believe that your personal documents, receipts, bills, and expiry dates belong strictly to you. 

* **Zero Account Registration:** We do not collect names, email addresses, phone numbers, or passwords.
* **100% On-Device OCR:** Optical Character Recognition (powered by RapidOCR ONNX & Google ML Kit) executes directly on your device's processor (CPU/NPU).
* **Zero Telemetry or Ad Tracking:** Memora contains no advertising identifiers, tracking beacons, or third-party behavioral analytics SDKs.
* **Local Sandboxed Storage:** All memories, images, and reminder alarms are stored strictly within your device's private, encrypted internal storage.

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
* When you capture an image of a receipt, document, or packaging, the image is saved to your phone's internal application storage directory (`context.filesDir`).
* Text and candidate dates are recognized locally using embedded on-device OCR models.
* No network connection is required for scanning or reminder scheduling.

### B. Optional Cloud AI Refinement
* Memora includes an optional AI refinement capability. 
* **Transparency & Double-Consent Guarantee:** This capability is **NEVER** executed automatically or in the background. It is triggered solely when you explicitly tap the "Refine with AI" button on the scan review bottom sheet.
* If you do not choose to refine, your data remains 100% on your device.

### C. Alarms & Notifications
* Reminders are scheduled locally using the Android operating system's native `AlarmManager`.
* Reminders trigger local status bar notifications; no external push notification service (such as FCM) is used to track your reminder schedule.

---

## 4. Your Rights as a Data Principal (DPDP Act, 2023)
Under Sections 11, 12, 13, and 14 of the Digital Personal Data Protection Act, 2023, you hold the following statutory rights:

1. **Right to Access Information (Section 11):**  
   You have immediate, direct access to all data stored within the app via the Home and History screens.
2. **Right to Correction & Erasure (Section 12):**  
   * You can edit any title, category, or reminder date at any time.
   * You can delete individual memories and associated image files with a simple swipe.
   * You can permanently purge all stored data via the **"Erase All Data"** button in *Settings > Privacy & Data Protection*.
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
In compliance with Section 13 of the DPDP Act, 2023, any privacy inquiries, feedback, or grievance redressal requests may be directed to our designated Data Protection Officer:

* **Designation:** Data Protection & Grievance Officer
* **Email:** `privacy@memora.app`
* **Response Window:** All inquiries are acknowledged within 48 hours and resolved within 30 days as mandated by statutory guidelines.
