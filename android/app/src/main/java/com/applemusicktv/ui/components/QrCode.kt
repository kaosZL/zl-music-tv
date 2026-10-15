package com.applemusicktv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** zxing 生成二维码 Bitmap */
fun qrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

/**
 * 扫码导入 overlay（ZL_Musicos 同款交互）：全屏遮罩 + 居中白底二维码，
 * 遥控器返回键关闭；下方「关闭」按钮保持原生焦点流。
 */
@Composable
fun QrImportOverlay(url: String, hint: String, onClose: () -> Unit) {
    val bitmap = remember(url) { runCatching { qrBitmap(url, 560) }.getOrNull() }
    androidx.activity.compose.BackHandler(enabled = true) { onClose() }
    Box(
        Modifier.fillMaxSize().background(Color(0xEE050507)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "手机扫码导入音源",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
        )
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "二维码",
                modifier = Modifier.size(420.dp).clip(RoundedCornerShape(18.dp)),
            )
        }
        Text(hint, fontSize = 13.sp, color = Color(0xFF9A9AA0),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp))
        Text("按遥控器返回键关闭", fontSize = 12.sp, color = Color(0xFF6A6A70),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}
