package it.venturini.poppy

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val vm: AppVM by viewModels()
    private lateinit var billing: Billing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.initPrefs(getSharedPreferences("poppy", MODE_PRIVATE))
        billing = Billing(this) { vm.message = R.string.tip_thanks }
        handleIntent(intent)
        setContent {
            PoppyTheme(vm.themeMode) {
                App(vm, billing, this, ::showLockPrompt)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val side = i?.getStringExtra("start_side")
        if (side != null) {
            vm.pendingStart = side
            i.removeExtra("start_side")
        }
    }

    override fun onStart() {
        super.onStart()
        if (vm.lockEnabled && !vm.locked && nowMs() - vm.lastUnlock > 60_000L && vm.lastUnlock != 0L) {
            vm.locked = true
        }
        if (vm.locked) showLockPrompt()
    }

    override fun onStop() {
        if (!vm.locked) vm.lastUnlock = nowMs()
        super.onStop()
    }

    fun showLockPrompt() {
        val can = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // nessun sensore/impronta registrata: non bloccare l'utente fuori dall'app
            vm.locked = false
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.locked = false
                vm.lastUnlock = nowMs()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    override fun onDestroy() {
        billing.close()
        super.onDestroy()
    }
}
