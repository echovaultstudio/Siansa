package app.siansa.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Reading music files' own tags, MusicBrainz IDs included (issue #352). */
class TagReaderTest {
    private val recording = "7d8a3f4e-1c2b-4a5d-9e6f-0123456789ab"
    private val artistA = "11111111-2222-3333-4444-555555555555"
    private val artistB = "66666666-7777-8888-9999-000000000000"
    private val album = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    // ---- Builders ----

    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun be24(n: Int) = byteArrayOf((n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun synchsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun bytes(vararg parts: ByteArray) = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)

    private fun vorbisComment(vararg entries: String): ByteArray {
        val vendor = utf8("test")
        return bytes(le32(vendor.size), vendor, le32(entries.size), *entries.map { utf8(it).let { e -> bytes(le32(e.size), e) } }.toTypedArray())
    }

    private fun flac(vararg entries: String): ByteArray {
        val streamInfo = ByteArray(34)
        val comment = vorbisComment(*entries)
        return bytes(ascii("fLaC"), byteArrayOf(0), be24(34), streamInfo, byteArrayOf(0x84.toByte()), be24(comment.size), comment, ByteArray(64))
    }

    private fun id3Frame(id: String, body: ByteArray, version: Int) =
        bytes(ascii(id), if (version == 4) synchsafe(body.size) else be32(body.size), byteArrayOf(0, 0), body)

    private fun id3Text(id: String, text: String, version: Int) = id3Frame(id, bytes(byteArrayOf(3), utf8(text)), version)
    private fun txxx(description: String, value: String, version: Int) = id3Frame("TXXX", bytes(byteArrayOf(3), utf8(description), byteArrayOf(0), utf8(value)), version)

    private fun id3(version: Int, vararg frames: ByteArray): ByteArray {
        val body = bytes(*frames, ByteArray(20))
        return bytes(ascii("ID3"), byteArrayOf(version.toByte(), 0, 0), synchsafe(body.size), body, byteArrayOf(0xFF.toByte(), 0xFB.toByte()), ByteArray(100))
    }

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = bytes(*content)
        return bytes(be32(body.size + 8), ascii(type), body)
    }

    private fun mp4Text(name: String, value: String) = box(name, box("data", be32(1), be32(0), utf8(value)))
    private fun mp4Freeform(name: String, value: String) =
        box("----", box("mean", be32(0), utf8("com.apple.iTunes")), box("name", be32(0), utf8(name)), box("data", be32(1), be32(0), utf8(value)))

    // ---- Tests ----

    @Test fun flacVorbisCommentsWithSeveralArtists() {
        val tags = TagReader.read(BytesRead(flac(
            "TITLE=Song", "ARTIST=A", "ARTIST=B", "ALBUM=Record", "ALBUMARTIST=A", "TRACKNUMBER=3/12", "DATE=2019-05-01",
            "MUSICBRAINZ_TRACKID=$recording", "MUSICBRAINZ_ARTISTID=$artistA", "MUSICBRAINZ_ARTISTID=$artistB",
            "MUSICBRAINZ_ALBUMID=$album", "REPLAYGAIN_TRACK_GAIN=-6.54 dB",
        )))
        assertEquals(listOf("Song"), tags["TITLE"])
        assertEquals(listOf("A", "B"), tags["ARTIST"])
        assertEquals(listOf(recording), tags["MUSICBRAINZ_TRACKID"])
        assertEquals(listOf(artistA, artistB), tags["MUSICBRAINZ_ARTISTID"])
        assertEquals(listOf(album), tags["MUSICBRAINZ_ALBUMID"])
        assertEquals(listOf("-6.54 dB"), tags["REPLAYGAIN_TRACK_GAIN"])
    }

    @Test fun id3v24TextTxxxAndUfid() {
        val ufid = id3Frame("UFID", bytes(ascii("http://musicbrainz.org"), byteArrayOf(0), ascii(recording)), 4)
        val file = id3(
            4,
            id3Text("TIT2", "Song", 4),
            id3Text("TPE1", "A\u0000B", 4),
            id3Text("TALB", "Record", 4),
            id3Text("TRCK", "7", 4),
            txxx("MusicBrainz Artist Id", "$artistA\u0000$artistB", 4),
            txxx("MusicBrainz Album Id", album, 4),
            txxx("REPLAYGAIN_TRACK_GAIN", "-3.10 dB", 4),
            ufid,
        )
        val tags = TagReader.read(BytesRead(file))
        assertEquals(listOf("Song"), tags["TITLE"])
        assertEquals(listOf("A", "B"), tags["ARTIST"])
        assertEquals(listOf("7"), tags["TRACKNUMBER"])
        assertEquals(listOf(recording), tags["MUSICBRAINZ_TRACKID"])
        assertEquals(listOf(artistA, artistB), tags["MUSICBRAINZ_ARTISTID"])
        assertEquals(listOf(album), tags["MUSICBRAINZ_ALBUMID"])
        assertEquals(listOf("-3.10 dB"), tags["REPLAYGAIN_TRACK_GAIN"])
    }

    @Test fun id3v23WithUtf16() {
        val utf16 = id3Frame("TIT2", bytes(byteArrayOf(1), "Café".toByteArray(Charsets.UTF_16)), 3)
        val tags = TagReader.read(BytesRead(id3(3, utf16, id3Text("TCON", "(17)Rock", 3))))
        assertEquals(listOf("Café"), tags["TITLE"])
        assertEquals(listOf("Rock"), tags["GENRE"])
    }

    @Test fun mp4AtomsWithFreeformMusicBrainzIds() {
        val ilst = box(
            "ilst",
            mp4Text("©nam", "Song"),
            mp4Text("©ART", "A"),
            mp4Text("aART", "A"),
            box("trkn", box("data", be32(0), be32(0), byteArrayOf(0, 0, 0, 4, 0, 10, 0, 0))),
            mp4Freeform("MusicBrainz Track Id", recording),
            mp4Freeform("MusicBrainz Artist Id", artistA),
        )
        val meta = box("meta", be32(0), box("hdlr", ByteArray(25)), ilst)
        // The audio first and "moov" at the end, as many files have it.
        val file = bytes(box("ftyp", ascii("M4A "), be32(0)), box("mdat", ByteArray(500)), box("moov", box("mvhd", ByteArray(100)), box("udta", meta)))
        val tags = TagReader.read(BytesRead(file))
        assertEquals(listOf("Song"), tags["TITLE"])
        assertEquals(listOf("A"), tags["ALBUMARTIST"])
        assertEquals(listOf("4/10"), tags["TRACKNUMBER"])
        assertEquals(listOf(recording), tags["MUSICBRAINZ_TRACKID"])
        assertEquals(listOf(artistA), tags["MUSICBRAINZ_ARTISTID"])
    }

    @Test fun opusTagsInOgg() {
        fun page(sequence: Int, packet: ByteArray): ByteArray {
            val lacing = ArrayList<Byte>()
            var left = packet.size
            while (left >= 255) { lacing += 255.toByte(); left -= 255 }
            lacing += left.toByte()
            return bytes(ascii("OggS"), byteArrayOf(0, 0), ByteArray(8), ByteArray(4), le32(sequence), ByteArray(4), byteArrayOf(lacing.size.toByte()), lacing.toByteArray(), packet)
        }
        val head = bytes(ascii("OpusHead"), ByteArray(11))
        val comment = bytes(ascii("OpusTags"), vorbisComment("title=Long one", "artist=A", "MUSICBRAINZ_TRACKID=$recording", "COMMENT=" + "x".repeat(600)))
        val tags = TagReader.read(BytesRead(bytes(page(0, head), page(1, comment))))
        assertEquals(listOf("Long one"), tags["TITLE"])
        assertEquals(listOf(recording), tags["MUSICBRAINZ_TRACKID"])
    }

    @Test fun unknownOrBrokenFilesGiveNothing() {
        assertTrue(TagReader.read(BytesRead(ByteArray(0))).isEmpty())
        assertTrue(TagReader.read(BytesRead(ascii("RIFF....WAVEfmt "))).isEmpty())
        // A FLAC whose comment block claims more than the file has.
        assertTrue(TagReader.read(BytesRead(bytes(ascii("fLaC"), byteArrayOf(0x84.toByte()), be24(9999), ByteArray(10)))).isEmpty())
    }

    @Test fun keysAreSpelledOneWay() {
        assertEquals("MUSICBRAINZ_ARTISTID", TagReader.canonical("MusicBrainz Artist Id"))
        assertEquals("MUSICBRAINZ_TRACKID", TagReader.canonical("musicbrainz_trackid"))
        assertEquals("MUSICBRAINZ_ALBUMARTISTID", TagReader.canonical("MusicBrainz Album Artist Id"))
        assertEquals("ALBUMARTIST", TagReader.canonical("Album Artist"))
        assertEquals("REPLAYGAIN_TRACK_GAIN", TagReader.canonical("replaygain_track_gain"))
    }
}
