package li.songe.morph.playground.report

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class ApngEncoderTest {
    @Test fun framesTimingChecksumsAndFallbackDecodeCorrectly() {
        val frames = listOf(intArrayOf(0, 255, 128, 64), intArrayOf(255, 0, 64, 128), intArrayOf(64, 128, 255, 0))
        val delays = listOf(10 to 30, 1 to 30, 9 to 30)
        val bytes = ApngEncoder.encode(2, 2, frames, delays)
        assertContentEquals(bytes, ApngEncoder.encode(2, 2, frames, delays))
        val stream = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(0x89504e470d0a1a0aUL.toLong(), stream.readLong())
        val chunks = mutableListOf<String>()
        var sequence = 0
        var controls = 0
        var decoded = 0
        while (stream.available() > 0) {
            val length = stream.readInt()
            val type = ByteArray(4).also(stream::readFully)
            val data = ByteArray(length).also(stream::readFully)
            val crc = stream.readInt().toLong() and 0xffffffffL
            assertEquals(CRC32().apply { update(type); update(data) }.value, crc)
            val name = String(type, Charsets.US_ASCII)
            chunks += name
            val content = DataInputStream(ByteArrayInputStream(data))
            when (name) {
                "acTL" -> { assertEquals(3, content.readInt()); assertEquals(0, content.readInt()) }
                "fcTL" -> {
                    assertEquals(sequence++, content.readInt())
                    assertEquals(2, content.readInt()); assertEquals(2, content.readInt())
                    assertEquals(0, content.readInt()); assertEquals(0, content.readInt())
                    assertEquals(delays[controls].first, content.readUnsignedShort())
                    assertEquals(delays[controls++].second, content.readUnsignedShort())
                    assertEquals(0, content.readUnsignedByte()); assertEquals(0, content.readUnsignedByte())
                }
                "IDAT", "fdAT" -> {
                    if (name == "fdAT") assertEquals(sequence++, content.readInt())
                    val raw = InflaterInputStream(content).readBytes()
                    assertContentEquals(byteArrayOf(0, (255-frames[decoded][0]).toByte(), (255-frames[decoded][1]).toByte(),
                        0, (255-frames[decoded][2]).toByte(), (255-frames[decoded][3]).toByte()), raw)
                    decoded++
                }
            }
        }
        assertEquals(listOf("IHDR", "acTL", "fcTL", "IDAT", "fcTL", "fdAT", "fcTL", "fdAT", "IEND"), chunks)
        assertEquals(3, decoded)
        val fallback = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(2, fallback.width)
        assertEquals(255, fallback.raster.getSample(0, 0, 0))
        assertEquals(0, fallback.raster.getSample(1, 0, 0))
    }

    @Test fun rejectsInconsistentFramesAndInvalidTiming() {
        assertFailsWith<IllegalArgumentException> { ApngEncoder.encode(2, 2, listOf(intArrayOf(0)), listOf(1 to 30)) }
        assertFailsWith<IllegalArgumentException> { ApngEncoder.encode(1, 1, listOf(intArrayOf(0)), listOf(1 to 0)) }
        assertFailsWith<IllegalArgumentException> { ApngEncoder.encode(1, 1, emptyList(), emptyList()) }
    }
}
