package dev.vibe.gallery

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
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
import dev.vibe.gallery.ui.PermState
import dev.vibe.gallery.ui.GalleryViewModel
import dev.vibe.gallery.ui.UiEvent
import dev.vibe.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val vm: GalleryViewModel by viewModels()
    private val perms = mutableStateOf(PermState(false, false, false, false))
    private var chainAllFiles = false

    /** Android 10 only: files we must delete one by one, asking permission for each. */
    private val legacyDeleteQueue = ArrayDeque<Uri>()

    private var pendingRename: UiEvent.Rename? = null

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val ok = result.resultCode == RESULT_OK
            pendingRename?.let { r ->
                pendingRename = null
                if (ok) applyRename(r)
                return@registerForActivityResult
            }
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
            if (chainAllFiles) {
                chainAllFiles = false
                if (!perms.value.allFiles) openAllFilesSettings()
            }
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

    /** Trash / restore / delete directly when we have All files access. True if it worked. */
    private fun direct(uris: List<Uri>, op: String): Boolean {
        if (Build.VERSION.SDK_INT < 30 || !Environment.isExternalStorageManager()) return false
        return runCatching {
            uris.forEach { u ->
                when (op) {
                    "trash" -> contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null)
                    "restore" -> contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, null, null)
                    else -> contentResolver.delete(u, null, null)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun updatePermission() {
        val images = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES)
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        val videos = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_VIDEO)
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (images && videos && !perms.value.media) vm.refresh()
        perms.value = PermState(
            images = images,
            videos = videos,
            allFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
            location = granted(Manifest.permission.ACCESS_MEDIA_LOCATION),
        )
    }

    /** One tap: ask for everything missing, then send the user to the All files page. */
    private fun grantAll() {
        val missing = buildList {
            if (!perms.value.media) addAll(mediaPermissions())
            if (!perms.value.location) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        chainAllFiles = !perms.value.allFiles
        if (missing.isEmpty()) {
            chainAllFiles = false
            openAllFilesSettings()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun openAllFilesSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
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
                    perms = perms.value,
                    onGrantAll = { grantAll() },
                    onRequestMedia = { permissionLauncher.launch(mediaPermissions()) },
                    onRequestAllFiles = { openAllFilesSettings() },
                    onRequestLocation = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION)) },
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
            is UiEvent.Rename -> rename(event)
            is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyRename(r: UiEvent.Rename): Boolean = try {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, r.name) }
        val n = contentResolver.update(r.uri, v, null, null)
        if (n > 0) vm.onRenamed(r.id, r.name) else Toast.makeText(this, "Couldn't rename", Toast.LENGTH_SHORT).show()
        n > 0
    } catch (e: RecoverableSecurityException) {
        pendingRename = r
        launchSender(e.userAction.actionIntent.intentSender)
        false
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= 30) {
            pendingRename = r
            launchSender(MediaStore.createWriteRequest(contentResolver, listOf(r.uri)).intentSender)
        } else {
            Toast.makeText(this, "Couldn't rename", Toast.LENGTH_SHORT).show()
        }
        false
    } catch (e: Exception) {
        Toast.makeText(this, "Couldn't rename (name may already exist)", Toast.LENGTH_SHORT).show()
        false
    }

    private fun rename(r: UiEvent.Rename) {
        applyRename(r)
    }

    private fun launchSender(sender: android.content.IntentSender) {
        intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private fun trash(uris: List<Uri>, toTrash: Boolean) {
        if (direct(uris, if (toTrash) "trash" else "restore")) {
            vm.onSystemDialogResult(true)
            return
        }
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
        if (direct(uris, "delete")) {
            vm.onSystemDialogResult(true)
            return
        }
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
