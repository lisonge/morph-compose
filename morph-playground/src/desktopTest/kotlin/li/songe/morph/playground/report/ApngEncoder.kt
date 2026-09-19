package li.songe.morph.playground.report

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Full-canvas 8-bit grayscale APNG. No timestamps, palettes or external encoder dependencies.
 * Format: https://www.w3.org/TR/png-3/#11fcTL
 */
internal object ApngEncoder {
    fun encode(width: Int, height: Int, frames: List<IntArray>, delays: List<Pair<Int, Int>>): ByteArray {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE)
        require(frames.isNotEmpty() && frames.size == delays.size)
        require(frames.all { it.size == width * height && it.all { alpha -> alpha in 0..255 } })
        require(delays.all { (numerator, denominator) -> numerator in 1..65535 && denominator in 1..65535 })
        val output = ByteArrayOutputStream()
        val stream = DataOutputStream(output)
        stream.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun data(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also {
            DataOutputStream(it).use(write)
        }.toByteArray()
        fun chunk(type: String, bytes: ByteArray) {
            val name = type.toByteArray(Charsets.US_ASCII)
            stream.writeInt(bytes.size)
            stream.write(name)
            stream.write(bytes)
            val crc = CRC32().apply { update(name); update(bytes) }
            stream.writeInt(crc.value.toInt())
        }
        chunk("IHDR", data {
            writeInt(width); writeInt(height); writeByte(8); writeByte(0)
            writeByte(0); writeByte(0); writeByte(0)
        })
        chunk("acTL", data { writeInt(frames.size); writeInt(0) }) // Infinite loop.
        var sequence = 0
        frames.forEachIndexed { index, alpha ->
            chunk("fcTL", data {
                writeInt(sequence++); writeInt(width); writeInt(height); writeInt(0); writeInt(0)
                writeShort(delays[index].first); writeShort(delays[index].second)
                writeByte(0); writeByte(0) // Keep canvas; replace it with the complete next frame.
            })
            val compressed = ByteArrayOutputStream().also { bytes ->
                DeflaterOutputStream(bytes).use { deflater ->
                    for (y in 0 until height) {
                        deflater.write(0) // PNG filter: None.
                        for (x in 0 until width) deflater.write(255 - alpha[y * width + x])
                    }
                }
            }.toByteArray()
            if (index == 0) chunk("IDAT", compressed)
            else chunk("fdAT", data { writeInt(sequence++); write(compressed) })
        }
        chunk("IEND", byteArrayOf())
        return output.toByteArray()
    }
}
