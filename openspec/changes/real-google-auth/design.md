# Design: Real Google Authentication

## Context
See `proposal.md` for motivation. Currently, `DefaultAuthRepository` returns hardcoded dummy user data (`user@gmail.com`). `app/build.gradle.kts` has commented dependencies for `firebase.auth`, `androidx.credentials`, and `googleid`. The Firebase project is `memora-df699` and the debug keystore SHA-1 is `34:F5:0D:43:97:8B:63:F1:14:11:D5:3B:CF:AD:B8:3E:18:97:E9:F9`.

## Goals / Non-Goals

**Goals:**
- Provide native, system-level Google Account picker using `androidx.credentials.CredentialManager` and `GoogleIdTokenCredential`.
- Authenticate the returned token with `FirebaseAuth` and expose a reactive `currentUser: StateFlow<AuthUser?>`.
- Allow guest users to upgrade seamlessly to Google-authenticated status from Onboarding and Profile sheet.
- Cleanly sign out and delete remote account upon request while preserving local database safety.

**Non-Goals:**
- Cloud Firestore bidirectional sync (this will be handled in a subsequent `cloud-sync` change).
- Custom email/password signup and verification workflows (Google Sign-In is the primary identity provider).

## Decisions

### 1. Modern Android Credential Manager over Legacy GoogleSignInClient
- **Rationale**: Google officially deprecated `GoogleSignInClient` and the legacy Play Services Auth SDK in favor of `androidx.credentials` + `com.google.android.libraries.identity.googleid:googleid`. Credential Manager supports passkeys, one-tap, and unified system bottom sheets across Android 14–16 while remaining backward-compatible down to API 26 via Play Services Auth.
- **Alternative considered**: Legacy `GoogleSignIn.getClient()` — rejected because it is deprecated and triggers Google Play Policy warnings on modern Android SDKs.

### 2. Platform `GoogleAuthClient` Abstraction
- **Rationale**: Credential Manager requires an Android `Context` (or `Activity`) to display the system credential picker UI. Isolating this interaction within `app.memora.platform.auth.GoogleAuthClient` keeps `DefaultAuthRepository` and domain UseCases testable without requiring Activity mocks.
- **Alternative considered**: Calling `CredentialManager` directly inside Composables — rejected because it scatters credential parsing, token extraction, and error dispatching across multiple screens.

### 3. Reactive State Synchronization via `FirebaseAuth.AuthStateListener`
- **Rationale**: `DefaultAuthRepository` will register an `AuthStateListener` with `FirebaseAuth`. When Firebase restores an existing session on app startup or invalidates a token, `_currentUser.value` immediately updates all Compose observers across the app.
- **Alternative considered**: Polling `FirebaseAuth.currentUser` or reading solely from `SharedPreferences` — rejected because SharedPreferences can become desynchronized from the actual Firebase token state.

### 4. Explicit Account Picker Confirmation
- **Rationale**: `GetGoogleIdOption.Builder().setAutoSelectEnabled(false)` ensures the account selection dialog always appears when tapped, preventing accidental logins if a device has multiple Google accounts configured.

## Risks / Trade-offs

- **[Risk] SHA-1 Fingerprint or Web Client ID Mismatch** &rarr; *Mitigation*: If the Web Client ID is not registered or SHA-1 is missing in Firebase Console, Credential Manager or Firebase throws `ApiException` (error code 10 / DEVELOPER_ERROR). We capture and log this explicitly in `AppLog` with a clear diagnostic message.
- **[Risk] User Dismisses the Dialog** &rarr; *Mitigation*: Catch `GetCredentialCancellationException` and treat it as a graceful user cancellation without displaying error banners or toasts.
- **[Risk] Network Failure During Authentication** &rarr; *Mitigation*: Catch `FirebaseNetworkException` and surface a clear "Network unavailable. Please check your connection." feedback while maintaining the user's current session state.

## Migration Plan
1. Uncomment authentication libraries in `app/build.gradle.kts`.
2. Add debug SHA-1 to Firebase project `memora-df699` and configure Google Sign-In.
3. Bind Web Client ID in `google-services.json` and `GoogleAuthClient`.
4. Replace mocked `DefaultAuthRepository` methods with Firebase Auth calls.
5. In case of issues, the mock repository fallback remains preserved via an environment flag or test profile.
