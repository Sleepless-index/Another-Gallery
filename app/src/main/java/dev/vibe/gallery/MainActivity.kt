package dev.vibe.gallery

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vibe.gallery.ui.GalleryRoot
import dev.vibe.gallery.ui.GalleryViewModel
import dev.vibe.gallery.ui.UiEvent
import dev.vibe.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val vm: GalleryViewModel by viewModels()
    private val hasPermission = mutableStateOf(false)

    /** Android 10 only: files we must delete one by one, asking permission for each. */
    private val legacyDeleteQueue = ArrayDeque<Uri>()

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val ok = result.resultCode == RESULT_OK
            if (Build.VERSION.SDK_INT < 30 && legacyDeleteQueue.isNotEmpty()) {
                if (ok) {
                    processLegacyDeletes()
                } else {
                    legacyDeleteQueue.clear()
                    vm.onSystemDialogResult(false)
                }
            } else {
                vm.onSystemDialogResult(ok)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermission()
        }

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasMediaPermission(): Boolean =
        mediaPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun updatePermission() {
        val granted = hasMediaPermission()
        if (granted && !hasPermission.value) vm.refresh()
        hasPermission.value = granted
    }

    private fun requestPermissions() {
        permissionLauncher.launch(mediaPermissions() + Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        updatePermission()
        vm.startObserving()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { handleEvent(it) }
            }
        }

        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            GalleryTheme(prefs) {
                GalleryRoot(
                    vm = vm,
                    hasPermission = hasPermission.value,
                    onRequestPermission = { requestPermissions() },
                    onAuthenticate = { ok -> authenticate(ok) },
                    onSecure = { secure ->
                        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    },
                )
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set a screen lock to use the Vault", Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lockVault()
    }

    override fun onStart() {
        super.onStart()
        // The user may have granted access from system settings.
        updatePermission()
    }

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Trash -> trash(event.uris, true)
            is UiEvent.Restore -> trash(event.uris, false)
            is UiEvent.DeleteForever -> deleteForever(event.uris)
            is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSender(sender: android.content.IntentSender) {
        intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun trash(uris: List<Uri>, toTrash: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pending = MediaStore.createTrashRequest(contentResolver, uris, toTrash)
            launchSender(pending.intentSender)
        } else if (toTrash) {
            // Android 10 has no system trash: this permanently deletes after the system asks.
            legacyDeleteQueue.clear()
            legacyDeleteQueue.addAll(uris)
            processLegacyDeletes()
        }
    }

    private fun deleteForever(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pending = MediaStore.createDeleteRequest(contentResolver, uris)
            launchSender(pending.intentSender)
        } else {
            legacyDeleteQueue.clear()
            legacyDeleteQueue.addAll(uris)
            processLegacyDeletes()
        }
    }

    private fun processLegacyDeletes() {
        while (legacyDeleteQueue.isNotEmpty()) {
            val uri = legacyDeleteQueue.first()
            try {
                contentResolver.delete(uri, null, null)
                legacyDeleteQueue.removeFirst()
            } catch (e: RecoverableSecurityException) {
                launchSender(e.userAction.actionIntent.intentSender)
                return
            } catch (e: SecurityException) {
                legacyDeleteQueue.removeFirst()
            }
        }
        vm.onSystemDialogResult(true)
    }
}
