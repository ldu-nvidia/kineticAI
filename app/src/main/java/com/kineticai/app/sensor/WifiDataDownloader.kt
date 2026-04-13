package com.kineticai.app.sensor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Downloads high-rate (200 Hz) IMU data from boot sensors over WiFi.
 *
 * After skiing, each M5StickC switches to WiFi mode and serves recorded
 * data over HTTP. This class fetches and parses that data.
 *
 * Binary protocol:
 *   Header: 1 byte side ('L'/'R') + 3 padding + 4 bytes record count (uint32 LE)
 *   Records: N × 28 bytes (4 byte timestamp uint32 LE + 6 × float32 LE)
 */
class WifiDataDownloader {

    companion object {
        private const val TAG = "WifiDataDownloader"
        private const val TIMEOUT_MS = 10_000
    }

    data class BootRecording(
        val side: Char,
        val records: List<HighRateImuRecord>,
        val markers: List<Long>,
    )

    data class HighRateImuRecord(
        val timestampMs: Long,
        val accelX: Float, val accelY: Float, val accelZ: Float,
        val gyroX: Float, val gyroY: Float, val gyroZ: Float,
    )

    data class BootStatus(
        val side: String,
        val records: Int,
        val markers: Int,
        val batteryV: Float,
        val uptimeMs: Long,
    )

    /**
     * Check if a boot sensor is reachable and get its status.
     */
    suspend fun getStatus(ipAddress: String): BootStatus? = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) return@withContext null

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            parseStatus(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get status from $ipAddress", e)
            null
        }
    }

    /**
     * Download all recorded IMU data from a boot sensor.
     */
    suspend fun downloadData(ipAddress: String): BootRecording? = withContext(Dispatchers.IO) {
        try {
            // Download binary data
            val dataUrl = URL("http://$ipAddress/data")
            val dataConn = dataUrl.openConnection() as HttpURLConnection
            dataConn.connectTimeout = TIMEOUT_MS
            dataConn.readTimeout = 30_000 // allow 30s for large downloads
            dataConn.requestMethod = "GET"

            if (dataConn.responseCode != 200) return@withContext null

            val rawData = BufferedInputStream(dataConn.inputStream).readBytes()
            dataConn.disconnect()

            if (rawData.size < 8) return@withContext null

            // Parse header
            val headerBuf = ByteBuffer.wrap(rawData, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
            val side = headerBuf.get().toInt().toChar()
            headerBuf.get(); headerBuf.get(); headerBuf.get() // skip padding
            val recordCount = headerBuf.int

            Log.i(TAG, "Downloaded $recordCount records from boot $side (${rawData.size} bytes)")

            // Parse records
            val recordSize = 28 // 4 + 6*4
            val records = ArrayList<HighRateImuRecord>(recordCount)
            var offset = 8

            for (i in 0 until recordCount) {
                if (offset + recordSize > rawData.size) break
                val buf = ByteBuffer.wrap(rawData, offset, recordSize).order(ByteOrder.LITTLE_ENDIAN)
                records.add(
                    HighRateImuRecord(
                        timestampMs = buf.int.toLong() and 0xFFFFFFFFL,
                        accelX = buf.float, accelY = buf.float, accelZ = buf.float,
                        gyroX = buf.float, gyroY = buf.float, gyroZ = buf.float,
                    )
                )
                offset += recordSize
            }

            // Download markers
            val markers = downloadMarkers(ipAddress)

            BootRecording(side = side, records = records, markers = markers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download data from $ipAddress", e)
            null
        }
    }

    /**
     * Download marker timestamps.
     */
    private suspend fun downloadMarkers(ipAddress: String): List<Long> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress/markers")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            if (conn.responseCode != 200) return@withContext emptyList()

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Simple JSON array parse: [123, 456, 789]
            json.trim().removeSurrounding("[", "]")
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download markers", e)
            emptyList()
        }
    }

    /**
     * Clear recorded data on the boot sensor.
     */
    suspend fun clearData(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ipAddress/clear")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            val success = conn.responseCode == 200
            conn.disconnect()
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear data on $ipAddress", e)
            false
        }
    }

    private fun parseStatus(json: String): BootStatus? {
        return try {
            fun extract(key: String): String {
                val pattern = "\"$key\"\\s*:\\s*\"?([^,\"\\}]+)\"?".toRegex()
                return pattern.find(json)?.groupValues?.get(1)?.trim() ?: ""
            }
            BootStatus(
                side = extract("side"),
                records = extract("records").toIntOrNull() ?: 0,
                markers = extract("markers").toIntOrNull() ?: 0,
                batteryV = extract("batteryV").toFloatOrNull() ?: 0f,
                uptimeMs = extract("uptimeMs").toLongOrNull() ?: 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse status JSON", e)
            null
        }
    }
}
