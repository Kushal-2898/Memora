package app.memora.domain.repository

import app.memora.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>
    suspend fun signInWithGoogle(
        idToken: String,
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null,
    ): Result<AuthUser>
    suspend fun signInWithEmail(email: String): Result<AuthUser>
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmailAndPassword(email: String, password: String): Result<AuthUser>
    suspend fun signOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}
