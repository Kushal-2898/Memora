package app.memora.platform.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

data class GoogleSignInAccount(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

class GoogleAuthClient(
    private val appContext: Context? = null,
) {
    /**
     * Checks if Google Sign-In has a valid Web Client ID configured.
     */
    fun isConfigured(context: Context, serverClientId: String = ""): Boolean {
        val resolvedClientId = resolveServerClientId(context, serverClientId)
        return resolvedClientId.isNotBlank() && !resolvedClientId.contains("placeholder")
    }

    fun resolveServerClientId(context: Context, serverClientId: String = ""): String {
        if (serverClientId.isNotBlank()) return serverClientId
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) runCatching { context.getString(resId) }.getOrDefault("") else ""
    }

    fun canLaunch(context: Context, serverClientId: String = ""): Boolean {
        if (runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess) return false
        return isConfigured(context, serverClientId) && findActivity(context) != null
    }

    private tailrec fun findActivity(context: Context): android.app.Activity? = when (context) {
        is android.app.Activity -> context
        is android.content.ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }

    /**
     * Launches the system Credential Manager bottom sheet to select a Google account.
     * Must be called with an Activity or Compose Activity Context to display UI.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String = ""): Result<GoogleSignInAccount> {
        val resolvedClientId = resolveServerClientId(activityContext, serverClientId)

        if (resolvedClientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Google Sign-In is not configured: serverClientId is empty. Enable Google Auth in Firebase Console to generate it.")
            )
        }

        val activity = findActivity(activityContext)
            ?: return Result.failure(IllegalStateException("Activity context required for Google Sign-In UI"))

        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(resolvedClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activity,
            )
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(
                    GoogleSignInAccount(
                        idToken = googleIdTokenCredential.idToken,
                        email = googleIdTokenCredential.id,
                        displayName = googleIdTokenCredential.displayName,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    )
                )
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancellationException("Google sign-in was dismissed by user"))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account found on this device", e))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun clearCredentialState() {
        val ctx = appContext ?: return
        try {
            val credentialManager = CredentialManager.create(ctx)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort clear
        }
    }
}
