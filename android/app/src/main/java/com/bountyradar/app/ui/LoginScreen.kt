package com.bountyradar.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One screen, two modes (sign in / sign up) toggled by a link. This is the
 *  "single login" — your BountyRadar account, not any platform password. */
@Composable
fun LoginScreen(vm: RadarViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BountyRadar", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(if (isSignUp) "Create your account" else "Sign in")

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

        Button(
            enabled = !busy && email.isNotBlank() && password.length >= 6,
            onClick = {
                busy = true; error = null
                scope.launch {
                    val result = if (isSignUp) vm.signUp(email, password)
                    else vm.signIn(email, password)
                    busy = false
                    result.onFailure { error = it.localizedMessage ?: "Failed" }
                }
            },
        ) { Text(if (isSignUp) "Sign up" else "Sign in") }

        TextButton(onClick = { isSignUp = !isSignUp; error = null }) {
            Text(if (isSignUp) "Have an account? Sign in" else "New here? Create an account")
        }
    }
}
