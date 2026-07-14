package de.curlybracket.grocery.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import de.curlybracket.grocery.R
import de.curlybracket.grocery.auth.AuthViewModel
import io.github.jan.supabase.exceptions.BadRequestRestException
import kotlinx.coroutines.launch

@Composable
internal fun SignInScreen(
    authViewModel: AuthViewModel,
) {
    var email by remember { mutableStateOf("") }
    val passwordState = rememberTextFieldState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val autofillManager = LocalAutofillManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    
    val errorInvalidCredentials = stringResource(R.string.sign_in_error_invalid_credentials)
    val errorGeneric = stringResource(R.string.sign_in_error_generic)

    fun signIn() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                authViewModel.signIn(email, passwordState.text.toString())
                autofillManager?.commit()
            } catch (e: Exception) {
                Logger.e("Sign-in failed", e)
                errorMessage = if (e is BadRequestRestException) {
                    errorInvalidCredentials
                } else {
                    e.message ?: errorGeneric
                }
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.sign_in_label_email)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() },
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
        )

        SecureTextField(
            state = passwordState,
            label = { Text(stringResource(R.string.sign_in_label_password)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            onKeyboardAction = { signIn() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .focusRequester(passwordFocusRequester)
                .semantics { contentType = ContentType.Password },
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        Button(
            onClick = { signIn() },
            modifier = Modifier.align(Alignment.End),
            enabled = !isLoading,
        ) {
            Text(
                if (isLoading) stringResource(R.string.sign_in_btn_signing_in)
                else stringResource(R.string.sign_in_title),
            )
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
