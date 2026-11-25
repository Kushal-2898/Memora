package app.memora.data.repository

import app.memora.data.local.OnboardingPreferences
import app.memora.domain.model.AuthUser
import app.memora.domain.repository.AuthRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultAuthRepository(
    private val onboardingPreferences: OnboardingPreferences? = null,
    private val firebaseAuth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onBeforeSignOut: (suspend () -> Unit)? = null,
    private val onSignOutCleanup: (suspend () -> Unit)? = null,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(loadCachedUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                val user = firebaseUser.toAuthUser()
                persistUser(user)
                _currentUser.value = user
            } else {
                onboardingPreferences?.clearAuth()
                _currentUser.value = null
            }
        }
    }

    private fun loadCachedUser(): AuthUser? {
        val fbUser = firebaseAuth?.currentUser
        if (fbUser != null) {
            return fbUser.toAuthUser()
        }
        val uid = onboardingPreferences?.getAuthUid() ?: return null
        return AuthUser(
            uid = uid,
            email = onboardingPreferences.getAuthEmail(),
            displayName = onboardingPreferences.getAuthDisplayName(),
            photoUrl = onboardingPreferences.getAuthPhotoUrl(),
            isAnonymous = false,
        )
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
        photoUrl: String?,
    ): Result<AuthUser> = runCatching {
        if (firebaseAuth != null) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user?.toAuthUser() ?: AuthUser(
                uid = "google_${idToken.hashCode()}",
                email = email ?: "user@gmail.com",
                displayName = displayName ?: "Google User",
                photoUrl = photoUrl,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        } else {
            val uid = "google_${idToken.hashCode()}"
            val user = AuthUser(
                uid = uid,
                email = email ?: "user@gmail.com",
                displayName = displayName ?: "Google User",
                photoUrl = photoUrl,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        }
    }

    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser> = runCatching {
        if (firebaseAuth != null) {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user?.toAuthUser() ?: AuthUser(
                uid = "email_${email.hashCode()}",
                email = email,
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                photoUrl = null,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        } else {
            val uid = "email_${email.hashCode()}"
            val user = AuthUser(
                uid = uid,
                email = email,
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                photoUrl = null,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        }
    }

    override suspend fun signUpWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser> = runCatching {
        if (firebaseAuth != null) {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user?.toAuthUser() ?: AuthUser(
                uid = "email_${email.hashCode()}",
                email = email,
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                photoUrl = null,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        } else {
            val uid = "email_${email.hashCode()}"
            val user = AuthUser(
                uid = uid,
                email = email,
                displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                photoUrl = null,
                isAnonymous = false,
            )
            persistUser(user)
            _currentUser.value = user
            user
        }
    }

    override suspend fun signInWithEmail(email: String): Result<AuthUser> {
        val uid = "email_${email.hashCode()}"
        val user = AuthUser(
            uid = uid,
            email = email,
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
            photoUrl = null,
            isAnonymous = false,
        )
        persistUser(user)
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            runCatching { onBeforeSignOut?.invoke() }
        }
        firebaseAuth?.signOut()
        onboardingPreferences?.clearAuth()
        onboardingPreferences?.setOnboardingCompleted(false)
        _currentUser.value = null
        withContext(ioDispatcher) {
            onSignOutCleanup?.invoke()
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val recentLoginError = try {
            firebaseAuth?.currentUser?.delete()?.await()
            null
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            e
        }
        if (recentLoginError != null) {
            firebaseAuth?.signOut()
        }
        onboardingPreferences?.clearAuth()
        onboardingPreferences?.setOnboardingCompleted(false)
        _currentUser.value = null
        withContext(ioDispatcher) {
            onSignOutCleanup?.invoke()
        }
        if (recentLoginError != null) {
            throw IllegalStateException(
                "Cloud data erased. For security, sign in once more and tap Delete Account to finalize login removal.",
                recentLoginError,
            )
        }
    }

    private fun persistUser(user: AuthUser) {
        onboardingPreferences?.setAuth(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
        )
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email ?: "user@gmail.com",
        displayName = displayName ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User",
        photoUrl = photoUrl?.toString(),
        isAnonymous = isAnonymous,
    )
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
