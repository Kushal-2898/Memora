# Tasks

## 1. Dependencies and Project Configuration

- [x] 1.1 Uncomment `firebase.auth`, `androidx.credentials`, `androidx.credentials.play.services`, and `googleid` in `app/build.gradle.kts` and verify `./gradlew assembleDebug` compiles
- [ ] 1.2 Validate that the debug SHA-1 (`34:F5:0D:43:97:8B:63:F1:14:11:D5:3B:CF:AD:B8:3E:18:97:E9:F9`) and Web Client ID are registered in Firebase Console

## 2. Platform Authentication Implementation

- [ ] 2.1 Create `GoogleAuthClient` in `app/src/main/java/app/memora/platform/auth/GoogleAuthClient.kt` wrapping `CredentialManager` and `GetGoogleIdOption`
- [ ] 2.2 Update `DefaultAuthRepository` to authenticate credentials via `FirebaseAuth` and expose reactive `currentUser: StateFlow<AuthUser?>` via `AuthStateListener`
- [ ] 2.3 Implement clean sign-out and account deletion (`firebaseUser.delete()`) in `DefaultAuthRepository`
- [ ] 2.4 Add unit tests for `DefaultAuthRepository` verifying auth state transitions, sign-in, and sign-out

## 3. UI and Flow Integration

- [ ] 3.1 Wire `GoogleAuthClient` into `OnboardingAccountPage` (Screen 7) and verify Google sign-in transitions to Home screen on account selection
- [ ] 3.2 Wire `GoogleAuthClient` into `ProfileBottomSheet` for guest upgrade, sign-out, and account deletion
- [ ] 3.3 Verify cancellation handling when dismissing the Google account picker without selecting an account
- [ ] 3.4 Build and verify live Google sign-in, sign-out, and session restoration on connected device (`ZA2237JDH6`)
