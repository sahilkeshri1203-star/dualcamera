package com.example.dualcamera




import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    DualCameraScreen(lifecycleOwner = this)
                }
            }
        }
    }
}

@Composable
fun DualCameraScreen(lifecycleOwner: LifecycleOwner) {
    val context: Context = LocalContext.current

    var hasCameraPermission: Boolean by remember {
        mutableStateOf<Boolean>(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted: Boolean ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(text = "Grant Camera Permission")
            }
        }
        return
    }

    val backPreviewView: PreviewView = remember<PreviewView> {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val frontPreviewView: PreviewView = remember<PreviewView> {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var statusMessage: String by remember {
        mutableStateOf<String>("")
    }

    LaunchedEffect(key1 = Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val concurrentInfos: List<List<CameraInfo>> =
                cameraProvider.availableConcurrentCameraInfos

            val dualPair: List<CameraInfo>? = concurrentInfos.firstOrNull { pair: List<CameraInfo> ->
                pair.any { it.lensFacing == CameraSelector.LENS_FACING_BACK } &&
                        pair.any { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
            }

            if (dualPair == null) {
                statusMessage = "This device does not support simultaneous dual cameras."
                return@addListener
            }

            val backInfo: CameraInfo =
                dualPair.first { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            val frontInfo: CameraInfo =
                dualPair.first { it.lensFacing == CameraSelector.LENS_FACING_FRONT }

            val backPreview: Preview = Preview.Builder().build().apply {
                surfaceProvider = backPreviewView.surfaceProvider
            }
            val frontPreview: Preview = Preview.Builder().build().apply {
                surfaceProvider = frontPreviewView.surfaceProvider
            }

            val backConfig = ConcurrentCamera.SingleCameraConfig(
                backInfo.cameraSelector,
                UseCaseGroup.Builder().addUseCase(backPreview).build(),
                lifecycleOwner
            )
            val frontConfig = ConcurrentCamera.SingleCameraConfig(
                frontInfo.cameraSelector,
                UseCaseGroup.Builder().addUseCase(frontPreview).build(),
                lifecycleOwner
            )

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(listOf(backConfig, frontConfig))
            } catch (e: Exception) {
                statusMessage = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen Back Camera
        AndroidView<PreviewView>(
            factory = { backPreviewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top-left Picture-in-Picture Front Camera
        AndroidView<PreviewView>(
            factory = { frontPreviewView },
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .width(130.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                .align(Alignment.TopStart)
        )

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        // Capture Shutter Button
        Button(
            onClick = {
                saveDualPhotoToGallery(context, backPreviewView, frontPreviewView)
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .size(76.dp)
                .border(4.dp, Color.Gray, CircleShape)
        ) {}
    }
}

fun saveDualPhotoToGallery(
    context: Context,
    backView: PreviewView,
    frontView: PreviewView
) {
    val backBitmap: Bitmap? = backView.bitmap
    val frontBitmap: Bitmap? = frontView.bitmap

    if (backBitmap == null || frontBitmap == null) {
        Toast.makeText(context, "Camera frames not ready yet", Toast.LENGTH_SHORT).show()
        return
    }

    val combinedBitmap = Bitmap.createBitmap(
        backBitmap.width,
        backBitmap.height,
        ARGB_8888
    )
    val canvas = Canvas(combinedBitmap)

    canvas.drawBitmap(backBitmap, 0f, 0f, null)

    val pipWidth: Int = (backBitmap.width * 0.30f).toInt()
    val pipHeight: Int = (pipWidth * (frontBitmap.height.toFloat() / frontBitmap.width)).toInt()
    val scaledFront: Bitmap = Bitmap.createScaledBitmap(frontBitmap, pipWidth, pipHeight, true)
    canvas.drawBitmap(scaledFront, 40f, 80f, null)

    val filename = "DUAL_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DualCamera")
        }
    }

    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )

    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        Toast.makeText(context, "Saved to Gallery (Pictures/DualCamera)!", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}