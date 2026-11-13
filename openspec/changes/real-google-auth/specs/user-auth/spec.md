# Spec Delta: user-auth

## Purpose
Provides secure user identity management using Android Credential Manager and Firebase Authentication for one-tap Google sign-in, session persistence, sign-out, and account deletion.

## ADDED Requirements

### Requirement: Google One-Tap Authentication
The system SHALL prompt the user with an Android system credential picker to select an available Google account and exchange the resulting ID token with Firebase Authentication.

#### Scenario: User signs in successfully via Google
- **WHEN** user taps "Continue with Google" and selects an authorized Google account
- **THEN** system exchanges the ID token for a Firebase session, updates the profile state with their display name, email, and photo URL, and transitions to the home screen

#### Scenario: User dismisses or cancels account selection
- **WHEN** user dismisses the system account picker without choosing an account
- **THEN** system remains on the current screen without throwing unhandled exceptions or showing error toasts

#### Scenario: Sign-in fails due to network outage
- **WHEN** user attempts Google sign-in without network connectivity
- **THEN** system displays a user-friendly error message indicating network unavailability and preserves their current unauthenticated state

### Requirement: Session Persistence and State Broadcast
The system SHALL broadcast the current authentication status reactively across the entire application and persist active sessions across process restarts.

#### Scenario: App launches with existing signed-in account
- **WHEN** user opens the app after previously signing in with Google
- **THEN** system restores the authenticated session and displays their Google profile information without prompting them to sign in again

#### Scenario: User operates in Guest Mode
- **WHEN** user chooses to skip authentication during onboarding
- **THEN** system operates in local-only mode and preserves all memories in local Room storage

### Requirement: Clean Sign Out
The system SHALL revoke local session tokens, clear Credential Manager state, and reset the authentication state upon user request.

#### Scenario: User signs out from profile sheet
- **WHEN** user clicks "Sign Out" in the Profile sheet
- **THEN** system signs out from Firebase, clears cached credentials, resets current user to null, and closes the profile sheet

### Requirement: Account Deletion and Data Removal
The system SHALL delete the remote user account in Firebase and wipe all associated local identity data upon explicit confirmation.

#### Scenario: User deletes account from settings
- **WHEN** signed-in user confirms account deletion in Settings
- **THEN** system deletes the Firebase user account, clears local preferences and authentication tokens, and returns the application to Guest mode
