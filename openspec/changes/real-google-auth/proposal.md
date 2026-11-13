# Proposal: Real Google Authentication

## Why
Currently, Memora uses a mock authentication repository (`DefaultAuthRepository`) that generates simulated tokens and dummy user profiles (`user@gmail.com`). To support multi-device synchronization, secure cloud backup, and verified user identity for store release, the application requires real Google Sign-In backed by Android's modern Credential Manager API and Firebase Authentication.

## What Changes
- Replace mocked Google sign-in with Android's Credential Manager (`androidx.credentials`) and Google Identity Services (`googleid`).
- Integrate Firebase Authentication (`firebase-auth`) to exchange Google ID tokens for authenticated Firebase user sessions.
- Preserve local-first guest mode while allowing seamless upgrade to a Google account during onboarding (Screen 7) or from the Profile sheet.
- Support clean sign-out (clearing Credential Manager state and Firebase Auth state) and GDPR/DPDP-compliant account deletion (`firebaseUser.delete()`).
- Gracefully handle user cancellation, missing Google accounts on device, and network outages with informative feedback.

## Capabilities

### New Capabilities
- `user-auth`: Authentic user lifecycle handling through Android Credential Manager and Firebase Authentication, covering Google One-Tap account selection, ID token verification, session persistence, sign-out, and account deletion.

### Modified Capabilities
None (this is the initial behavioral specification for user authentication).

## Impact
- **Dependencies**: Uncomment and wire `libs.firebase.auth`, `libs.androidx.credentials`, `libs.androidx.credentials.play.services`, and `libs.googleid` in `app/build.gradle.kts`.
- **Configuration**: Requires Web Client ID from Google Cloud / Firebase Console and binding the Android debug/release SHA-1 certificate fingerprints in `memora-df699`.
- **Architecture**: Adds `GoogleAuthClient` in `platform/auth`, updates `DefaultAuthRepository` with Firebase Auth, and wires UI triggers in `OnboardingScreen` and `ProfileBottomSheet`.
