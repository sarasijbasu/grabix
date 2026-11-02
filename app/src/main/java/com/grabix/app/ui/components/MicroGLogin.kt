package com.grabix.app.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.grabix.app.ui.MainViewModel
import com.grabix.app.util.MicroG
import kotlinx.coroutines.launch

private const val YT_HOME = "https://m.youtube.com/"
private const val GOOGLE_LOGIN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

/**
 * Returns an action that signs the Grabix browser into Google through microG:
 * pick the account → microG returns a web-login link → the browser opens it and gets
 * the session cookies. Falls back to Google's own sign-in page when microG is missing
 * or refuses.
 */
@Composable
fun rememberMicroGLogin(vm: MainViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = MicroG.accountFromResult(result.data)
        val activity = context as? Activity
        if (account == null || activity == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { MicroG.webLoginUrl(activity, account, YT_HOME) }
                .onSuccess { url ->
                    vm.openInBrowser(url)
                    vm.toast("Signing in as ${account.name}…")
                }
                .onFailure {
                    vm.toast("microG couldn't sign in (${it.message ?: "unknown error"}). Opening Google sign-in instead.")
                    vm.openInBrowser(GOOGLE_LOGIN)
                }
        }
    }
    return remember(launcher) {
        val action: () -> Unit = {
            if (MicroG.installed(context).isEmpty()) {
                vm.toast("microG (GmsCore) is not installed. Opening Google sign-in instead.")
                vm.openInBrowser(GOOGLE_LOGIN)
            } else {
                try {
                    launcher.launch(MicroG.chooseAccountIntent())
                } catch (_: Exception) {
                    vm.openInBrowser(GOOGLE_LOGIN)
                }
            }
        }
        action
    }
}
