package uk.co.zenithal.carrel.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.PROFILE_PATH
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.Carrel
import uk.co.zenithal.carrel.ui.you.Avatar
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The cropped square is sent at this size, as the website's; the API shrinks it again to what it stores. */
private const val OUTPUT_SIZE = 512

/** Chosen pictures are opened no bigger than this along their long edge, plenty to crop a 512 square from. */
private const val MAX_SIDE = 2048

/** How far the picture can be zoomed in, from just covering the circle. */
private const val MAX_ZOOM = 4f

private const val UNAVAILABLE = "Profile pictures are unavailable right now. Try again later."

/**
 * The reader's picture, above their other details. Add or Change opens the phone's photo picker straight away; a chosen
 * picture opens for framing in a circle, then Save uploads it.
 */
@Composable
fun PictureSetting(profile: Profile) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val colors = Carrel.colors
    val saving = rememberSaving()
    var chosen by rememberSaveable { mutableStateOf<Uri?>(null) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var removing by remember { mutableStateOf(false) }
    var hint by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            hint = null
            saving.error = null
            picture = null
            chosen = uri
        }
    }
    val choose = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    // Opened first, so a file Android can't read is turned away before framing.
    LaunchedEffect(chosen) {
        val uri = chosen ?: return@LaunchedEffect
        picture = withContext(Dispatchers.IO) { runCatching { openPicture(context, uri) }.getOrNull() }
        if (picture == null) {
            chosen = null
            saving.error = "Carrel can’t read that picture. Try a JPEG, PNG, or WebP."
        }
    }

    suspend fun show(updated: Profile, message: String) {
        container.store.save(PROFILE_PATH, updated, Profile.serializer())
        hint = message
        chosen = null
        picture = null
    }

    Text("PROFILE PICTURE", style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = colors.inkSoft)
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Avatar(profile.avatarUrl, profile.username.orEmpty())
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LinkButton(if (profile.avatarUrl != null) "Change" else "Add", { if (!saving.busy) choose() }, color = colors.ink)
                if (profile.avatarUrl != null) {
                    LinkButton(if (removing) "Removing…" else "Remove", {
                        removing = true
                        hint = null
                        saving.run(UNAVAILABLE) {
                            try {
                                show(container.api.delete("/profile/picture", Profile.serializer()), "Picture removed.")
                            } finally {
                                removing = false
                            }
                        }
                    }, color = colors.ink)
                }
            }
            if (chosen == null) {
                saving.error?.let { FormMessage(it, Tone.Error) } ?: hint?.let { Text(it, style = Carrel.type.body, color = colors.inkFaint) }
            }
        }
    }

    val bitmap = picture
    if (chosen != null && bitmap != null) {
        PictureCropper(
            bitmap,
            saving,
            onSave = { area ->
                saving.run(UNAVAILABLE) {
                    val png = withContext(Dispatchers.Default) { cropPicture(bitmap, area) }
                    show(container.api.upload("/profile/picture", png, ContentType.Image.PNG, Profile.serializer()), "Picture saved.")
                }
            },
            onChooseAnother = choose,
            onCancel = {
                chosen = null
                picture = null
                saving.error = null
            },
        )
    }
}

/**
 * The chosen picture under a round window, full screen: drag it and pinch to zoom until the part to keep is in the
 * circle. The picture always covers the circle, so there's never an empty edge.
 */
@Composable
private fun PictureCropper(
    bitmap: Bitmap,
    saving: Saving,
    onSave: (android.graphics.Rect) -> Unit,
    onChooseAnother: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Carrel.colors
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(Size.Zero) }
    val frame = remember(bitmap, area) { CropFrame(bitmap.width, bitmap.height, area) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dialog has its own window, whose bars' icons need to match the paper too.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
        Column(Modifier.fillMaxSize().background(colors.paper).safeDrawingPadding().padding(16.dp)) {
            Text("Frame your picture", style = Carrel.type.heading, color = colors.ink)
            Text("Drag it, and pinch to zoom.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp).onSizeChanged { area = it.toSize() }) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(frame) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                offset = frame.clamp(offset + pan, zoom)
                            }
                        },
                ) {
                    val scale = frame.scale(zoom)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val topLeft = center + offset - Offset(width / 2, height / 2)
                    drawImage(
                        image,
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    // Paper over everything outside the circle, so what's kept stands out.
                    val circle = Path().apply { addOval(Rect(center, frame.diameter / 2)) }
                    clipPath(circle, ClipOp.Difference) { drawRect(colors.paper.copy(alpha = 0.8f)) }
                    drawCircle(colors.ruleStrong, frame.diameter / 2, center, style = Stroke(1.dp.toPx()))
                }
            }
            saving.error?.let { FormMessage(it, Tone.Error, Modifier.padding(bottom = 12.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PrimaryButton(if (saving.busy) "Saving…" else "Save", { onSave(frame.crop(offset, zoom)) }, enabled = frame.ready && !saving.busy)
                LinkButton("Choose another", { if (!saving.busy) onChooseAnother() })
                LinkButton("Cancel", { if (!saving.busy) onCancel() })
            }
        }
    }
}

/**
 * Where the picture sits under the circle: the circle fills the shorter side of `area`, less a margin, and at zoom 1
 * the picture just covers it. Offsets are the picture's centre from the circle's, in screen pixels.
 */
private class CropFrame(private val width: Int, private val height: Int, area: Size) {
    val diameter = max(min(area.width, area.height) - 2 * MARGIN, 0f)
    val ready get() = diameter > 0f

    fun scale(zoom: Float) = diameter / min(width, height) * zoom

    /** Keeps the picture covering the circle. */
    fun clamp(offset: Offset, zoom: Float): Offset {
        val scale = scale(zoom)
        val x = max((width * scale - diameter) / 2, 0f)
        val y = max((height * scale - diameter) / 2, 0f)
        return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
    }

    /** The square of the picture inside the circle, in the picture's own pixels. */
    fun crop(offset: Offset, zoom: Float): android.graphics.Rect {
        val scale = scale(zoom)
        val side = diameter / scale
        val left = width / 2f - offset.x / scale - side / 2
        val top = height / 2f - offset.y / scale - side / 2
        return android.graphics.Rect(left.roundToInt(), top.roundToInt(), (left + side).roundToInt(), (top + side).roundToInt())
    }

    private companion object {
        const val MARGIN = 8f
    }
}

/** The chosen square, as a PNG (which keeps any transparency) for the upload. */
private fun cropPicture(bitmap: Bitmap, area: android.graphics.Rect): ByteArray {
    val square = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(square).drawBitmap(bitmap, area, android.graphics.Rect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
    return ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/**
 * Opens a chosen picture the right way up (camera photos are often stored sideways, with a note to turn them), and
 * shrunk so a large photo doesn't use up the app's memory. Null if it can't be read.
 */
private fun openPicture(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Turns the picture as its note says. Kept in ordinary memory, so it can be cropped.
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val degrees = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}

/** Halves the picture until its long edge is within MAX_SIDE. */
private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (max(width, height) / sample > MAX_SIDE) sample *= 2
    return sample
}
