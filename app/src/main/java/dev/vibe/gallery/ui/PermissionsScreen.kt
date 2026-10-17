package dev.vibe.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibe.gallery.ui.theme.GIcons

/** What the app has been allowed to do. */
data class PermState(val media: Boolean, val allFiles: Boolean, val location: Boolean)

/**
 * Lists every permission the app can use, what it is for, and whether it has been granted.
 * Used as the first-run gate (back = null) and later from Settings.
 */
@Composable
fun PermissionsScreen(
    state: PermState,
    onRequestMedia: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRequestLocation: () -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
        if (onBack != null) {
            ScreenHeader(
                title = "Permissions",
                subtitle = "What the gallery can access",
                leading = {
                    GIconButton(GIcons.Back, "Back", onClick = onBack, flat = true)
                    Box(Modifier.size(6.dp))
                },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (onBack == null) 36.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack == null) {
                Text("Set up your gallery", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                Text(
                    "Everything stays on your phone. Here is what the gallery asks for and why.",
                    fontSize = 14.sp, color = cs.onSurfaceVariant,
                )
            }
            PermCard(
                icon = GIcons.Photos,
                title = "Photos and videos",
                required = true,
                why = "Needed to show your photos and videos.",
                granted = state.media,
                onAllow = onRequestMedia,
            )
            PermCard(
                icon = GIcons.Move,
                title = "All files access",
                required = false,
                why = "Lets you move files between albums and to the SD card, and delete or rename without extra prompts. Without it, everything else still works.",
                granted = state.allFiles,
                onAllow = onRequestAllFiles,
            )
            PermCard(
                icon = GIcons.Info,
                title = "Photo locations",
                required = false,
                why = "Shows where a photo was taken in its details.",
                granted = state.location,
                onAllow = onRequestLocation,
            )
        }
        if (onBack == null) {
            Text(
                if (state.media) "Continue" else "Allow photos to continue",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (state.media) cs.onPrimary else cs.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (state.media) cs.primary else cs.surfaceVariant)
                    .then(if (state.media) Modifier.bounceClick(onClick = onContinue) else Modifier)
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermCard(
    icon: ImageVector,
    title: String,
    required: Boolean,
    why: String,
    granted: Boolean,
    onAllow: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = cs.onBackground, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                Text(if (required) "Required" else "Optional", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Icon(GIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(13.dp))
                    }
                    Text("Allowed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                }
            } else {
                Text(
                    "Allow",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.primary)
                        .bounceClick(onClick = onAllow)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
        Text(why, fontSize = 13.sp, color = cs.onSurfaceVariant)
    }
}
