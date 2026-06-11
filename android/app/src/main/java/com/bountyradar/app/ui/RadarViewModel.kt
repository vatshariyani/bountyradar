package com.bountyradar.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bountyradar.app.data.Program
import com.bountyradar.app.data.ProgramRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

data class AuthState(val signedIn: Boolean, val email: String? = null)

class RadarViewModel(
    private val repo: ProgramRepository = ProgramRepository(),
) : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    private val _authState = MutableStateFlow(currentAuth())
    val authState: StateFlow<AuthState> = _authState

    val query = MutableStateFlow("")

    /** Programs filtered live by the search box, newest first. */
    val programs: StateFlow<List<Program>> =
        combine(repo.observePrograms(), query) { list, q ->
            if (q.isBlank()) list
            else list.filter { p ->
                p.name.contains(q, true) ||
                    p.platform.contains(q, true) ||
                    p.tags.any { it.contains(q, true) } ||
                    p.scope.any { it.contains(q, true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun currentAuth() =
        AuthState(auth.currentUser != null, auth.currentUser?.email)

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        _authState.value = currentAuth()
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        _authState.value = currentAuth()
    }

    fun signOut() {
        auth.signOut()
        _authState.value = currentAuth()
    }
}
