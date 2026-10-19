package sleepless_index.haze

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sleepless_index.haze.data.Entry
import sleepless_index.haze.data.LayoutMode
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.ui.GIconButton
import sleepless_index.haze.ui.GalleryViewModel
import sleepless_index.haze.ui.MediaCollection
import sleepless_index.haze.ui.ScreenHeader
import sleepless_index.haze.ui.bounceClick
import sleepless_index.haze.ui.theme.GIcons
import sleepless_index.haze.ui.theme.GalleryTheme

/** Shows up in other apps' "choose a photo" lists (e.g. Discord profile picture). */
class PickerActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()
    private val granted = mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { check() }

    private fun perms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun check() {
        val ok = perms().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (ok && !granted.value) vm.refresh()
        granted.value = ok
    }

    private fun accepts(item: MediaItem, types: List<String>): Boolean = types.any { t ->
        when {
            t == "*/*" || t.isBlank() -> true
            t.endsWith("/*") -> item.mime.startsWith(t.removeSuffix("*"))
            else -> item.mime.equals(t, ignoreCase = true)
        }
    }

    private fun finishWith(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val out = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size > 1 || intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) {
            val clip = ClipData.newRawUri("media", uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            out.clipData = clip
        }
        setResult(RESULT_OK, out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        check()
        vm.startObserving()
        if (!granted.value) permLauncher.launch(perms())

        val multi = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        val types: List<String> = (intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()
            ?: listOfNotNull(intent.type)).ifEmpty { listOf("*/*") }

        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val all by vm.items.collectAsStateWithLifecycle()
            GalleryTheme(prefs) {
                val cs = MaterialTheme.colorScheme
                val list = remember(all, types) { all.filter { accepts(it, types) } }
                val entries = remember(list) { list.map { Entry.Media(it) as Entry } }
                var chosen by remember { mutableStateOf(emptySet<Long>()) }

                Box(Modifier.fillMaxSize().background(cs.background)) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        ScreenHeader(
                            title = "Choose a photo",
                            subtitle = if (multi && chosen.isNotEmpty()) "${chosen.size} selected" else "%,d items".format(list.size),
                            leading = {
                                GIconButton(GIcons.Close, "Cancel", onClick = { finish() })
                                Box(Modifier.padding(6.dp))
                            },
                        )
                        MediaCollection(
                            entries = entries,
                            layout = LayoutMode.GRID,
                            columns = 3,
                            selected = chosen,
                            selectMode = multi,
                            favorites = emptySet(),
                            onClick = { item ->
                                if (multi) {
                                    chosen = if (item.id in chosen) chosen - item.id else chosen + item.id
                                } else {
                                    finishWith(listOf(item.uri))
                                }
                            },
                            onSetSelected = { id, on -> if (multi) chosen = if (on) chosen + id else chosen - id },
                            bottomPad = 96.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (multi && chosen.isNotEmpty()) {
                        Text(
                            "Done (${chosen.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .systemBarsPadding()
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(cs.primary)
                                .bounceClick(onClick = {
                                    finishWith(list.filter { it.id in chosen }.map { it.uri })
                                })
                                .padding(horizontal = 36.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
