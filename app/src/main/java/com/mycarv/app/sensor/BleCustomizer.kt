package com.mycarv.app.sensor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sends customization commands to boot sensors over BLE.
 * Handles text, emoji, color, display mode, and image/GIF upload.
 */
class BleCustomizer(private val bleManager: BleImuManager) {

    companion object {
        private const val TAG = "BleCustomizer"
        const val IMG_WIDTH = 135
        const val IMG_HEIGHT = 240

        const val MODE_METRICS = 0
        const val MODE_EMOJI = 1
        const val MODE_STEALTH = 2
        const val MODE_SHOWOFF = 3
        const val MODE_IMAGE = 4
        const val MODE_GIF = 5
        const val MODE_PARTY = 6

        const val PWR_SKIING = 0
        const val PWR_LIFT = 1
        const val PWR_SLEEP = 2
    }

    private fun writeCmd(gatt: BluetoothGatt?, data: ByteArray) {
        if (gatt == null) return
        val service = gatt.getService(BleImuManager.SERVICE_UUID) ?: return
        val cmdChar = service.getCharacteristic(
            java.util.UUID.fromString("4d430004-0000-1000-8000-00805f9b34fb")
        ) ?: return
        @SuppressLint("MissingPermission")
        cmdChar.setValue(data)
        @SuppressLint("MissingPermission")
        gatt.writeCharacteristic(cmdChar)
    }

    fun setDisplayMode(mode: Int) {
        val data = byteArrayOf(0x10, mode.toByte())
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setColorTheme(r: Int, g: Int, b: Int) {
        val data = byteArrayOf(0x11, r.toByte(), g.toByte(), b.toByte())
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setCustomText(slot: Int, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8).take(19).toByteArray()
        val data = ByteArray(2 + textBytes.size)
        data[0] = 0x12
        data[1] = slot.toByte()
        textBytes.copyInto(data, 2)
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setEmojiForQuality(quality: Int, emoji: String) {
        val textBytes = emoji.toByteArray(Charsets.UTF_8).take(19).toByteArray()
        val data = ByteArray(2 + textBytes.size)
        data[0] = 0x13
        data[1] = quality.toByte()
        textBytes.copyInto(data, 2)
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setCelebrationText(text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8).take(19).toByteArray()
        val data = ByteArray(1 + textBytes.size)
        data[0] = 0x20
        textBytes.copyInto(data, 1)
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setStreakText(text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8).take(19).toByteArray()
        val data = ByteArray(1 + textBytes.size)
        data[0] = 0x21
        textBytes.copyInto(data, 1)
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    // ── Power Management Commands ──

    fun setPowerState(state: Int) {
        val data = byteArrayOf(0x30, state.toByte())
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun playTone(freqHz: Int, durationMs: Int) {
        val dur10ms = (durationMs / 10).coerceIn(1, 255)
        val data = byteArrayOf(0x31, (freqHz and 0xFF).toByte(), ((freqHz shr 8) and 0xFF).toByte(), dur10ms.toByte())
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun setLcdBrightness(brightness: Int) {
        val data = byteArrayOf(0x32, brightness.coerceIn(0, 255).toByte())
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun forceWake() {
        val data = byteArrayOf(0x33)
        writeCmd(bleManager.leftGatt, data)
        writeCmd(bleManager.rightGatt, data)
    }

    fun clearCustomImage() {
        writeCmd(bleManager.leftGatt, byteArrayOf(0x1B))
        writeCmd(bleManager.rightGatt, byteArrayOf(0x1B))
    }

    /**
     * Upload a bitmap image to one or both boots.
     * Scales to 135×240, converts to RGB565, sends in BLE chunks.
     */
    suspend fun uploadImage(context: Context, imageUri: Uri, toBoth: Boolean = true) {
        val bitmap = loadAndScaleBitmap(context, imageUri) ?: return
        val rgb565 = bitmapToRgb565(bitmap)

        val targets = mutableListOf<BluetoothGatt?>()
        if (toBoth) {
            targets.add(bleManager.leftGatt)
            targets.add(bleManager.rightGatt)
        } else {
            targets.add(bleManager.leftGatt)
        }

        for (gatt in targets) {
            if (gatt == null) continue

            // Begin upload
            val beginCmd = ByteArray(5)
            beginCmd[0] = 0x14
            ByteBuffer.wrap(beginCmd, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(rgb565.size)
            writeCmd(gatt, beginCmd)
            delay(50)

            // Send chunks (max 19 bytes payload per BLE write)
            val chunkSize = 19
            var offset = 0
            while (offset < rgb565.size) {
                val remaining = rgb565.size - offset
                val thisChunk = minOf(chunkSize, remaining)
                val chunkCmd = ByteArray(1 + thisChunk)
                chunkCmd[0] = 0x15
                System.arraycopy(rgb565, offset, chunkCmd, 1, thisChunk)
                writeCmd(gatt, chunkCmd)
                offset += thisChunk
                delay(10) // BLE write pacing
            }

            // Commit
            writeCmd(gatt, byteArrayOf(0x16))
            delay(50)

            Log.i(TAG, "Uploaded ${rgb565.size} bytes to ${gatt.device.name}")
        }
    }

    /**
     * Upload multiple frames as a GIF animation.
     */
    suspend fun uploadGifFrames(
        context: Context,
        frameUris: List<Uri>,
        delayMs: Int = 200,
        toBoth: Boolean = true,
    ) {
        val frameCount = minOf(frameUris.size, 10)
        val targets = mutableListOf<BluetoothGatt?>()
        if (toBoth) {
            targets.add(bleManager.leftGatt)
            targets.add(bleManager.rightGatt)
        } else {
            targets.add(bleManager.leftGatt)
        }

        for (gatt in targets) {
            if (gatt == null) continue

            // Begin GIF upload
            val beginCmd = ByteArray(7)
            beginCmd[0] = 0x17
            beginCmd[1] = frameCount.toByte()
            beginCmd[2] = 0; beginCmd[3] = 0; beginCmd[4] = 0
            ByteBuffer.wrap(beginCmd, 5, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(delayMs.toShort())
            writeCmd(gatt, beginCmd)
            delay(50)

            for (i in 0 until frameCount) {
                val bitmap = loadAndScaleBitmap(context, frameUris[i]) ?: continue
                val rgb565 = bitmapToRgb565(bitmap)

                // Send frame chunks
                val chunkSize = 19
                var offset = 0
                while (offset < rgb565.size) {
                    val remaining = rgb565.size - offset
                    val thisChunk = minOf(chunkSize, remaining)
                    val chunkCmd = ByteArray(1 + thisChunk)
                    chunkCmd[0] = 0x18
                    System.arraycopy(rgb565, offset, chunkCmd, 1, thisChunk)
                    writeCmd(gatt, chunkCmd)
                    offset += thisChunk
                    delay(10)
                }

                // Commit this frame
                writeCmd(gatt, byteArrayOf(0x19))
                delay(50)
            }

            Log.i(TAG, "Uploaded $frameCount GIF frames to ${gatt.device.name}")
        }
    }

    private fun loadAndScaleBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            Bitmap.createScaledBitmap(original, IMG_WIDTH, IMG_HEIGHT, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private fun bitmapToRgb565(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val buf = ByteBuffer.allocate(w * h * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val rgb565 = ((r and 0xF8) shl 8) or ((g and 0xFC) shl 3) or (b shr 3)
                buf.putShort(rgb565.toShort())
            }
        }
        return buf.array()
    }
}
