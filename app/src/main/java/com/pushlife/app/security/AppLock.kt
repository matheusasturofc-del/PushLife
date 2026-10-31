package com.pushlife.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity

// trava do app: digital (ou rosto) e, se preferir, a senha/PIN/padrão do próprio celular
object AppLock {
    // voltou do fundo depois disso: pede de novo (abrir o Spotify ou um link e voltar rápido não trava)
    const val GRACE_MS = 30_000L
    const val REQUEST_CODE = 4710

    private fun prefs(context: Context) = context.getSharedPreferences("security", Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }

    // sem senha/digital no celular não dá pra travar
    fun deviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    // pede a digital ou a senha; no Android 8 (sem a tela nova) usa a tela de senha do sistema,
    // e a resposta chega no onActivityResult com REQUEST_CODE
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent == null) onSuccess() else {
                pendingSuccess = onSuccess
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQUEST_CODE)
            }
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private var pendingSuccess: (() -> Unit)? = null

    // resposta da tela de senha do Android 8
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val success = pendingSuccess
        pendingSuccess = null
        if (resultCode == Activity.RESULT_OK) success?.invoke()
        return true
    }

    private const val KEY_ENABLED = "app_lock"
}
