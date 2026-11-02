package com.grabix.app.vault

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.grabix.app.GrabixApp
import com.grabix.app.data.DownloadEntity
import com.grabix.app.util.Format
import com.grabix.app.util.Storage
import java.io.File

/**
 * Private folder: files are moved into app-internal storage, hidden from the
 * gallery and other apps, and protected by a PIN and optional fingerprint/face.
 */
object Vault {

    suspend fun isPinSet(): Boolean = GrabixApp.instance.settings.current().vaultPinHash != null

    suspend fun setPin(pin: String) = GrabixApp.instance.settings.setVaultPin(Format.sha256("grabix:$pin"))

    suspend fun checkPin(pin: String): Boolean =
        GrabixApp.instance.settings.current().vaultPinHash == Format.sha256("grabix:$pin")

    suspend fun moveIn(context: Context, item: DownloadEntity) {
        val path = item.filePath ?: return
        val src = File(path)
        if (!src.exists()) return
        val dest = Storage.moveFile(src, Storage.vaultDir(context))
        Storage.scan(context, path) // removes it from the gallery index
        GrabixApp.instance.db.downloads().update(item.copy(filePath = dest.absolutePath, inVault = true))
    }

    suspend fun moveOut(context: Context, item: DownloadEntity) {
        val path = item.filePath ?: return
        val src = File(path)
        if (!src.exists()) return
        val settings = GrabixApp.instance.settings.current()
        val dest = Storage.moveFile(src, Storage.outputDir(context, settings, item.kind))
        Storage.scan(context, dest.absolutePath)
        GrabixApp.instance.db.downloads().update(item.copy(filePath = dest.absolutePath, inVault = false))
    }

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock private folder")
            .setAllowedAuthenticators(authenticators())
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("Use PIN") }
            .build()
        prompt.authenticate(info)
    }
}
