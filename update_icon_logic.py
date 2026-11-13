with open('app/src/main/java/app/memora/presentation/capture/components/CameraPreviewView.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Add painterResource import if missing
if 'import androidx.compose.ui.res.painterResource' not in code:
    code = code.replace(
        'import androidx.compose.ui.res.stringResource',
        'import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.res.stringResource'
    )

target_icon = """                    Icon(
                        imageVector = when (flashMode) {
                            CameraFlashMode.AUTO -> Icons.Filled.FlashAuto
                            CameraFlashMode.ON -> Icons.Filled.FlashOn
                            CameraFlashMode.OFF -> Icons.Filled.FlashOff
                        },
                        contentDescription = when (flashMode) {
                            CameraFlashMode.AUTO -> stringResource(R.string.capture_flash_auto)
                            CameraFlashMode.ON -> stringResource(R.string.capture_flash_on)
                            CameraFlashMode.OFF -> stringResource(R.string.capture_flash_off)
                        },
                        tint = when (flashMode) {
                            CameraFlashMode.ON -> Color(0xFFFFD54F)
                            CameraFlashMode.AUTO -> Color.White
                            CameraFlashMode.OFF -> Color.White.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(24.dp),
                    )"""

replacement_icon = """                    if (flashMode == CameraFlashMode.OFF) {
                        Icon(
                            painter = painterResource(R.drawable.ic_flash_off),
                            contentDescription = stringResource(R.string.capture_flash_off),
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (flashMode == CameraFlashMode.AUTO) Icons.Filled.FlashAuto else Icons.Filled.FlashOn,
                            contentDescription = stringResource(if (flashMode == CameraFlashMode.AUTO) R.string.capture_flash_auto else R.string.capture_flash_on),
                            tint = if (flashMode == CameraFlashMode.ON) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }"""

assert target_icon in code, "target_icon not found"
code = code.replace(target_icon, replacement_icon, 1)

with open('app/src/main/java/app/memora/presentation/capture/components/CameraPreviewView.kt', 'w', encoding='utf-8', newline='') as f:
    f.write(code)

print("Updated CameraPreviewView.kt with custom ic_flash_off drawable!")
