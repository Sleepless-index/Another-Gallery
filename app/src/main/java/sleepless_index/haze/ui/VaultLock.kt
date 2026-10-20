package sleepless_index.haze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The vault's own lock: a PIN, a pattern or a password. Only a salted hash is stored. */
object VaultLockStore {
    const val PIN = "pin"
    const val PATTERN = "pattern"
    const val PASSWORD = "password"

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 60_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Stored as "type|salt|hash". */
    fun make(type: String, secret: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return type + "|" + salt.joinToString("") { "%02x".format(it) } + "|" + hash(secret, salt)
    }

    fun typeOf(stored: String): String? = stored.split("|").takeIf { it.size == 3 }?.get(0)

    fun verify(stored: String, secret: String): Boolean {
        val parts = stored.split("|")
        if (parts.size != 3) return false
        val salt = ByteArray(parts[1].length / 2) { parts[1].substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return MessageDigest.isEqual(hash(secret, salt).toByteArray(), parts[2].toByteArray())
    }
}

private fun typeName(type: String) = when (type) {
    VaultLockStore.PIN -> "PIN"
    VaultLockStore.PATTERN -> "pattern"
    else -> "password"
}

/** Collects a secret of the given type. [resetKey] clears the input (after a wrong try). */
@Composable
fun SecretInput(type: String, resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    when (type) {
        VaultLockStore.PIN -> PinPad(resetKey, onSubmit, onTooShort)
        VaultLockStore.PATTERN -> PatternPad(resetKey, onSubmit, onTooShort)
        else -> PasswordEntry(resetKey, onSubmit, onTooShort)
    }
}

@Composable
private fun PinPad(resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var pin by remember(resetKey) { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) cs.primary else cs.surfaceVariant)
                )
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(if (key == "OK") cs.primary else cs.surfaceVariant)
                            .bounceClick {
                                when (key) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length >= 4) onSubmit(pin) else onTooShort("Use at least 4 digits")
                                    else -> if (pin.length < 12) pin += key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            fontSize = if (key[0].isDigit()) 24.sp else 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (key == "OK") cs.onPrimary else cs.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternPad(resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val line = cs.primary
    val dot = cs.onSurfaceVariant
    val path = remember(resetKey) { mutableStateListOf<Int>() }
    var finger by remember(resetKey) { mutableStateOf<Offset?>(null) }
    var sizePx by remember { mutableStateOf(1f) }

    fun center(i: Int): Offset {
        val cell = sizePx / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }

    fun hit(p: Offset) {
        for (i in 0 until 9) {
            if (i !in path && (center(i) - p).getDistance() < sizePx / 7f) path.add(i)
        }
    }

    Canvas(
        Modifier
            .size(260.dp)
            .pointerInput(resetKey) {
                detectDragGestures(
                    onDragStart = { o -> sizePx = size.width.toFloat(); path.clear(); finger = o; hit(o) },
                    onDrag = { change, _ -> change.consume(); finger = change.position; hit(change.position) },
                    onDragEnd = {
                        finger = null
                        if (path.size >= 4) {
                            onSubmit(path.joinToString("-"))
                        } else {
                            path.clear()
                            onTooShort("Connect at least 4 dots")
                        }
                    },
                    onDragCancel = { finger = null; path.clear() },
                )
            }
    ) {
        sizePx = size.width
        for (k in 1 until path.size) {
            drawLine(line, center(path[k - 1]), center(path[k]), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
        val f = finger
        if (f != null && path.isNotEmpty()) {
            drawLine(line, center(path.last()), f, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
        for (i in 0 until 9) {
            val on = i in path
            drawCircle(if (on) line else dot, radius = if (on) 11.dp.toPx() else 7.dp.toPx(), center = center(i))
            if (on) drawCircle(line.copy(alpha = 0.25f), radius = 24.dp.toPx(), center = center(i), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun PasswordEntry(resetKey: Int, onSubmit: (String) -> Unit, onTooShort: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember(resetKey) { mutableStateOf("") }
    fun go() { if (text.length >= 4) onSubmit(text) else onTooShort("Use at least 4 characters") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { go() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Continue",
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(cs.primary)
                .bounceClick(onClick = { go() })
                .padding(horizontal = 36.dp, vertical = 14.dp),
        )
    }
}

/** Pick a lock type, enter it, confirm it. */
@Composable
fun LockSetup(title: String, onDone: (type: String, secret: String) -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var type by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val t = type
        if (t == null) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
            Text("Choose how to lock the Vault", fontSize = 13.sp, color = cs.onSurfaceVariant)
            listOf(VaultLockStore.PIN to "PIN", VaultLockStore.PATTERN to "Pattern", VaultLockStore.PASSWORD to "Password").forEach { (k, label) ->
                Text(
                    label,
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceVariant)
                        .bounceClick(onClick = { type = k })
                        .padding(vertical = 16.dp),
                )
            }
        } else {
            Text(
                if (first == null) "Enter a new ${typeName(t)}" else "Confirm your ${typeName(t)}",
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
            )
            Text(msg ?: " ", fontSize = 13.sp, color = if (msg != null) cs.error else cs.onSurfaceVariant)
            SecretInput(
                type = t, resetKey = key,
                onTooShort = { msg = it; key++ },
                onSubmit = { s ->
                    val f = first
                    if (f == null) {
                        first = s; msg = null; key++
                    } else if (f == s) {
                        onDone(t, s)
                    } else {
                        first = null; msg = "Didn't match, try again"; key++
                    }
                },
            )
        }
        Text(
            "Cancel",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .bounceClick(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Asks for the saved lock. */
@Composable
fun LockEntry(stored: String, onUnlock: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val type = VaultLockStore.typeOf(stored) ?: VaultLockStore.PIN
    var msg by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) }
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Enter your ${typeName(type)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
        Text(msg ?: " ", fontSize = 13.sp, color = cs.error)
        SecretInput(
            type = type, resetKey = key,
            onTooShort = { msg = it; key++ },
            onSubmit = { s ->
                if (VaultLockStore.verify(stored, s)) onUnlock() else { msg = "Wrong ${typeName(type)}"; key++ }
            },
        )
        Text(
            "Back",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .bounceClick(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
