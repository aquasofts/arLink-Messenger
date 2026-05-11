package com.nearlink.messenger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap

@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "qr code", modifier = modifier)
}
