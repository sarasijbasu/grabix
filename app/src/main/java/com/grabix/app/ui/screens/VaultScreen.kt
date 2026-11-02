package com.grabix.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.grabix.app.GrabixApp
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.vault.Vault
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by GrabixApp.instance.settings.flow.collectAsState(initial = null)
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    val s = settings ?: return
    val hasPin = s.vaultPinHash != null
    val canBio = remember { Vault.canUseBiometric(context) }

    fun tryBiometric() {
        val act = context as? FragmentActivity ?: return
        Vault.promptBiometric(act, onSuccess = { unlocked = true }, onFail = { error = it })
    }

    LaunchedEffect(hasPin) {
        if (hasPin && s.vaultBiometric && canBio && !unlocked) tryBiometric()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Private folder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (unlocked) IconButton(onClick = { unlocked = false; pin = "" }) { Icon(Icons.Filled.Lock, "Lock") }
        }

        if (!unlocked) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (hasPin) "Enter your PIN" else "Create a PIN to protect private videos and music",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Files here are hidden from the gallery and other apps. They are removed if Grabix is uninstalled, so move them out first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN (4–8 digits)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!hasPin) OutlinedTextField(
                    value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirm PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        scope.launch {
                            error = null
                            if (!hasPin) {
                                when {
                                    pin.length < 4 -> error = "Use at least 4 digits"
                                    pin != confirm -> error = "PINs don't match"
                                    else -> { Vault.setPin(pin); unlocked = true }
                                }
                            } else if (Vault.checkPin(pin)) unlocked = true else error = "Wrong PIN"
                            pin = ""; confirm = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LockOpen, null); Text(if (hasPin) "  Unlock" else "  Set PIN")
                }
                if (hasPin && canBio) OutlinedButton(onClick = { tryBiometric() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Fingerprint, null); Text("  Use fingerprint / face")
                }
            }
            return@Column
        }

        val items by GrabixApp.instance.db.downloads().observeVault().collectAsState(initial = emptyList())
        if (items.isEmpty()) {
            EmptyState(
                Icons.Filled.Lock, "Private folder is empty",
                "In Downloads or Library, tap ⋮ → Move to private folder."
            )
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.id }) { DoneRow(it, items, inVault = true) }
        }
    }
}
