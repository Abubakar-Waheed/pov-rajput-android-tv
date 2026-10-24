package dev.pov.tv

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DolbyVisionCompatibilityTest {
    // Synthetic layer-zero Main10 SPS profile-tier-level header; no film data.
    private fun sps(profile: Int = 2) = byteArrayOf(0, 0, 0, 1, 0x42, 1, 1, profile.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 120)
    private fun color(transfer: Int = C.COLOR_TRANSFER_ST2084) = ColorInfo.Builder()
        .setColorSpace(C.COLOR_SPACE_BT2020).setColorRange(C.COLOR_RANGE_LIMITED).setColorTransfer(transfer).build()
    private fun format(profile: Int = 7, transfer: Int = C.COLOR_TRANSFER_ST2084) = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.${profile.toString().padStart(2, '0')}.06")
        .setWidth(1920).setHeight(1080).setColorInfo(color(transfer)).setInitializationData(listOf(sps())).build()
    private fun base(format: Format, native: Boolean = false, mode: DolbyVisionMode = DolbyVisionMode.AUTO,
        transfers: Set<Int> = setOf(C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG)) =
        DolbyVisionCompatibility.baseFormat(format, mode, native, transfers)

    @Test fun `P7 and P8 HDR10 fallback retains real codec level and color information`() {
        for (profile in listOf(7, 8)) {
            val original = format(profile)
            val mapped = checkNotNull(base(original))
            assertEquals(MimeTypes.VIDEO_H265, mapped.sampleMimeType)
            assertTrue(mapped.codecs!!.startsWith("hvc1.2."))
            assertEquals(original.colorInfo, mapped.colorInfo)
            assertEquals(original.width, mapped.width)
            assertEquals("dvhe.${profile.toString().padStart(2, '0')}.06", original.codecs)
        }
    }

    @Test fun `native DV stays native automatically and standard player never rewrites formats`() {
        assertNull(base(format(), native = true))
        assertNotNull(base(format(), native = true, mode = DolbyVisionMode.HDR_BASE))
        assertNull(base(format(), mode = DolbyVisionMode.STANDARD))
    }

    @Test fun `Profile5 encrypted unknown and non-DV formats never become HDR10`() {
        assertNull(base(format(5)))
        assertNull(base(format(4)))
        assertNull(base(format().buildUpon().setDrmInitData(DrmInitData("cenc")).build()))
        assertNull(base(format().buildUpon().setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build()))
        assertNull(base(format().buildUpon().setCodecs(null).build()))
        assertNull(base(format().buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build()))
    }

    @Test fun `HLG fallback is only accepted for P8 with an HLG display`() {
        assertNotNull(base(format(8, C.COLOR_TRANSFER_HLG)))
        assertNull(base(format(7, C.COLOR_TRANSFER_HLG)))
        assertNull(base(format(8, C.COLOR_TRANSFER_HLG), transfers = setOf(C.COLOR_TRANSFER_ST2084)))
        assertNull(base(format(), transfers = emptySet()))
    }

    @Test fun `unverified color and initialization data fail closed`() {
        assertNull(base(format().buildUpon().setColorInfo(null).build()))
        assertNull(base(format().buildUpon().setColorInfo(color().buildUpon().setColorSpace(C.COLOR_SPACE_BT709).build()).build()))
        assertNull(base(format().buildUpon().setColorInfo(color().buildUpon().setColorRange(C.COLOR_RANGE_FULL).build()).build()))
        assertNull(base(format().buildUpon().setInitializationData(emptyList()).build()))
        assertNull(base(format().buildUpon().setInitializationData(listOf(byteArrayOf(0, 0, 1, 0x42, 1))).build()))
        assertNull(base(format().buildUpon().setInitializationData(listOf(sps(1))).build()))
    }

    private fun nal(type: Int, layer: Int = 0, short: Boolean = false) =
        (if (short) byteArrayOf(0, 0, 1) else byteArrayOf(0, 0, 0, 1)) +
            byteArrayOf(((type shl 1) or (layer shr 5)).toByte(), (((layer and 31) shl 3) or 1).toByte(), 65, 66, 67)

    @Test fun `base layer filtering removes RPU FEL and all nonzero layers without changing base bytes`() {
        val kept = nal(32) + nal(33, short = true) + nal(1) + nal(39)
        val mixed = nal(32) + nal(62) + nal(33, short = true) + nal(63) + nal(1, 1) + nal(1) + nal(1, 32) + nal(39)
        for (buffer in listOf(ByteBuffer.allocate(mixed.size), ByteBuffer.allocateDirect(mixed.size))) {
            buffer.put(mixed).flip()
            val size = DolbyVisionCompatibility.retainBaseLayer(buffer)
            val actual = ByteArray(size).also { buffer.position(0); buffer.get(it) }
            assertArrayEquals(kept, actual)
        }
        val onlyEnhancement = ByteBuffer.wrap(nal(62) + nal(63) + nal(1, 1))
        assertEquals(0, DolbyVisionCompatibility.retainBaseLayer(onlyEnhancement))
    }

    @Test fun `length-prefixed truncated and invalid temporal headers cannot be reinterpreted`() {
        for (bad in listOf(byteArrayOf(0, 0, 0, 3, 2, 1, 44), byteArrayOf(0, 0, 1, 0x42), byteArrayOf(0, 0, 1, 2, 0))) {
            assertThrows(IllegalArgumentException::class.java) { DolbyVisionCompatibility.retainBaseLayer(ByteBuffer.wrap(bad)) }
        }
    }
}
