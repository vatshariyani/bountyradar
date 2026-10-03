package com.bountyradar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bountyradar.app.ui.components.PrimaryButton
import com.bountyradar.app.ui.components.RadarLogo
import com.bountyradar.app.ui.components.radarFieldColors
import com.bountyradar.app.ui.theme.Radar
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: RadarViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canSubmit = email.isNotBlank() && password.length >= 6

    fun submit() {
        if (!canSubmit || busy) return
        focus.clearFocus()
        busy = true; error = null
        scope.launch {
            val result = if (isSignUp) vm.signUp(email, password) else vm.signIn(email, password)
            busy = false
            result.onFailure { error = it.localizedMessage ?: "Could not sign in. Try again." }
        }
    }

    // Left-aligned, scrollable and keyboard-aware so the button is never covered.
    Column(
        Modifier
            .fillMaxSize()
            .background(Radar.Bg)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        RadarLogo(64.dp, pulse = true)
        Spacer(Modifier.height(28.dp))
        Text(
            if (isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Be first to every new bug bounty program.",
            style = MaterialTheme.typography.bodyLarge, color = Radar.Muted,
        )
        Spacer(Modifier.height(36.dp))

        Text("Email", style = MaterialTheme.typography.labelMedium, color = Radar.Muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            placeholder = { Text("you@example.com") }, singleLine = true,
            shape = Radar.InnerShape, colors = radarFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text("Password", style = MaterialTheme.typography.labelMedium, color = Radar.Muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            placeholder = { Text("At least 6 characters") }, singleLine = true,
            shape = Radar.InnerShape, colors = radarFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error.orEmpty(), color = Radar.Danger, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            if (isSignUp) "Create account" else "Sign in",
            onClick = ::submit, enabled = canSubmit, loading = busy, arrow = !busy,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(Radar.PillShape)
                .clickable(role = Role.Button) { isSignUp = !isSignUp; error = null },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isSignUp) "I already have an account" else "Create a new account",
                style = MaterialTheme.typography.labelLarge, color = Radar.Accent,
            )
        }
    }
}
