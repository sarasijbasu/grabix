package com.grabix.app.util

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in through microG (GmsCore), the same companion app ReVanced uses.
 *
 * Google refuses passwords typed into an embedded WebView, so instead we ask the
 * microG account on the phone for a one-time "web login" link. Opening that link in the
 * Grabix browser sets the normal Google / YouTube cookies, which the download engine then
 * reuses for age-restricted, private and members-only videos.
 */
object MicroG {

    data class Provider(val packageName: String, val label: String)

    /** Known GmsCore builds, most specific first. */
    private val KNOWN = listOf(
        "app.revanced.android.gms" to "ReVanced GmsCore",
        "com.mgoogle.android.gms" to "Vanced microG",
        "com.google.android.gms" to "microG / Google Play services",
    )

    /** Account type registered by microG and by Google Play services. */
    const val GOOGLE_ACCOUNT_TYPE = "com.google"

    fun installed(context: Context): List<Provider> {
        val pm = context.packageManager
        return KNOWN.mapNotNull { (pkg, fallback) ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                if (!ai.enabled) null else Provider(pkg, pm.getApplicationLabel(ai).toString().ifBlank { fallback })
            }.getOrNull()
        }
    }

    /**
     * System account picker. All account types are allowed because some GmsCore builds
     * register their own type; the user simply picks their Google account.
     */
    fun chooseAccountIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null, null, null, null, null, null, null
        )

    fun accountFromResult(data: Intent?): Account? {
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return null
        val type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) ?: GOOGLE_ACCOUNT_TYPE
        return Account(name, type)
    }

    /**
     * Asks microG for a web-login link for [continueUrl]. microG may show its own
     * "allow Grabix to use this account" screen first.
     */
    suspend fun webLoginUrl(activity: Activity, account: Account, continueUrl: String): String =
        suspendCancellableCoroutine { cont ->
            val scope = "weblogin:service=youtube&continue=" + Uri.encode(continueUrl)
            AccountManager.get(activity).getAuthToken(
                account, scope, Bundle(), activity,
                { future ->
                    try {
                        val token = future.result.getString(AccountManager.KEY_AUTHTOKEN)
                        if (token.isNullOrBlank()) {
                            cont.resumeWithException(IllegalStateException("microG did not return a sign-in link"))
                        } else {
                            // Usually a full URL; a bare token is turned into a merge-session link.
                            val url = if (token.startsWith("http")) token
                            else "https://accounts.google.com/MergeSession?source=Grabix&uberauth=" +
                                Uri.encode(token) + "&continue=" + Uri.encode(continueUrl)
                            cont.resume(url)
                        }
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resumeWithException(t)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }

    /**
     * With microG on, the browser hides that it is a WebView ("; wv") so Google's own
     * sign-in page is offered as a fallback when no microG account is available.
     */
    fun browserUserAgent(webViewUa: String): String =
        webViewUa.replace("; wv)", ")").replace(Regex("""\sVersion/\d+(\.\d+)*"""), "")
}
