package com.vault.app.presentation.orgauth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OrgAuthScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    viewModel: OrgAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onAuthenticated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (state.mode == OrgAuthMode.LOGIN) "Sign in" else "Create organization",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))

        TabRow(selectedTabIndex = if (state.mode == OrgAuthMode.LOGIN) 0 else 1) {
            Tab(
                selected = state.mode == OrgAuthMode.LOGIN,
                onClick = { viewModel.onModeChanged(OrgAuthMode.LOGIN) },
                text = { Text("Sign in") },
            )
            Tab(
                selected = state.mode == OrgAuthMode.REGISTER,
                onClick = { viewModel.onModeChanged(OrgAuthMode.REGISTER) },
                text = { Text("Register org") },
            )
        }
        Spacer(Modifier.height(24.dp))

        if (state.mode == OrgAuthMode.REGISTER) {
            OutlinedTextField(
                value = state.orgName,
                onValueChange = viewModel::onOrgNameChanged,
                label = { Text("Organization name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (state.mode == OrgAuthMode.LOGIN) "Sign in" else "Create organization")
            }
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not now")
        }
    }
}
