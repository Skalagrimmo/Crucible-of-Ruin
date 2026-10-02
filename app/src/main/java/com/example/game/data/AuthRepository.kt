package com.example.game.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    suspend fun signInWithGoogle(): Boolean {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val clientIdResource = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (clientIdResource == 0) {
                _errorMessage.value = "Google Sign-In is not configured"
                _isLoading.value = false
                return false
            }
            val serverClientId = context.getString(clientIdResource)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                auth.signInWithCredential(authCredential).await()
                _currentUser.value = auth.currentUser
                _isLoading.value = false
                return true
            } else {
                _errorMessage.value = "Unexpected credential type"
                _isLoading.value = false
                return false
            }
        } catch (e: GetCredentialCancellationException) {
            android.util.Log.w("AuthRepository", "Sign-in cancelled by user", e)
            _errorMessage.value = "Sign-in cancelled"
            _isLoading.value = false
            return false
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Authentication error", e)
            _errorMessage.value = e.localizedMessage ?: "Authentication failed"
            _isLoading.value = false
            return false
        }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }
}
