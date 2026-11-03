package app.siansa.local

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.Locale

/**
 * A file read at any position (issue #352): a music file opened through Android's file picker, or bytes in a test.
 * MP4 keeps its tags wherever the "moov" box is, often at the end, so tags can't be read from the start alone.
 */
interface RandomRead {
    val size: Long

    /** Up to [length] bytes from [position]; fewer at the end of the file. */
    fun read(position: Long, length: Int): ByteArray
}

class BytesRead(private val bytes: ByteArray) : RandomRead {
    override val size: Long get() = bytes.size.toLong()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= bytes.size || position < 0) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

class ChannelRead(private val channel: FileChannel) : RandomRead {
    override val size: Long get() = channel.size()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= size || position < 0 || length <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - position).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n <= 0) break
            at += n
        }
        return buffer.array().copyOf(buffer.position())
    }
}

/**
 * Reads a music file's text tags itself (issue #352): Vorbis comments (FLAC, Ogg Vorbis, Opus), ID3v2 (MP3) and MP4
 * atoms (M4A, ALAC). Android's MediaMetadataRetriever gives the title, artist and album, but not the MusicBrainz IDs
 * Picard writes (MUSICBRAINZ_TRACKID, _ARTISTID, _ALBUMID), nor ReplayGain or several artists, so those come from here.
 *
 * Keys come out the Vorbis way, upper case: TITLE, ARTIST, ARTISTS, ALBUM, ALBUMARTIST, TRACKNUMBER, DISCNUMBER, DATE,
 * GENRE, REPLAYGAIN_TRACK_GAIN… and the MusicBrainz ones as MUSICBRAINZ_TRACKID (the recording), MUSICBRAINZ_ARTISTID,
 * MUSICBRAINZ_ALBUMID, MUSICBRAINZ_ALBUMARTISTID. A key can have several values (one per artist).
 */
object TagReader {
    /** The tags of the file, empty when it's none of the kinds read here or can't be read. */
    fun read(file: RandomRead): Map<String, List<String>> = runCatching {
        val head = file.read(0, 12)
        val out = Tags()
        when {
            head.startsWith("ID3") -> {
                val end = readId3(file, out)
                // FLAC with an ID3 tag in front of it (it happens).
                if (file.read(end, 4).startsWith("fLaC")) readFlac(file, end + 4, out)
            }
            head.startsWith("fLaC") -> readFlac(file, 4, out)
            head.startsWith("OggS") -> readOgg(file, out)
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> readMp4(file, out)
        }
        out.map
    }.getOrElse { emptyMap() }

    /** Tags as they're found, keys made the same whatever the format. */
    internal class Tags {
        val map = LinkedHashMap<String, MutableList<String>>()
        fun add(key: String, value: String) {
            val k = canonical(key) ?: return
            val v = value.trim().trimEnd('\u0000')
            if (v.isEmpty()) return
            val list = map.getOrPut(k) { ArrayList() }
            if (v !in list) list += v
        }
    }

    private val MB_KEYS = mapOf(
        "MUSICBRAINZTRACKID" to "MUSICBRAINZ_TRACKID",
        "MUSICBRAINZRECORDINGID" to "MUSICBRAINZ_TRACKID",
        "MUSICBRAINZARTISTID" to "MUSICBRAINZ_ARTISTID",
        "MUSICBRAINZALBUMID" to "MUSICBRAINZ_ALBUMID",
        "MUSICBRAINZRELEASEID" to "MUSICBRAINZ_ALBUMID",
        "MUSICBRAINZALBUMARTISTID" to "MUSICBRAINZ_ALBUMARTISTID",
        "MUSICBRAINZRELEASETRACKID" to "MUSICBRAINZ_RELEASETRACKID",
        "MUSICBRAINZRELEASEGROUPID" to "MUSICBRAINZ_RELEASEGROUPID",
        "ALBUMARTIST" to "ALBUMARTIST",
        "ALBUMARTISTS" to "ALBUMARTISTS",
    )

    /**
     * One spelling per key: "MusicBrainz Artist Id" (ID3, MP4) and "MUSICBRAINZ_ARTISTID" (Vorbis) are the same,
     * "ALBUM ARTIST" and "ALBUMARTIST" too. Null for a key with nothing in it.
     */
    internal fun canonical(key: String): String? {
        val upper = key.trim().uppercase(Locale.ROOT)
        if (upper.isEmpty()) return null
        val squashed = upper.replace(" ", "").replace("_", "")
        return MB_KEYS[squashed] ?: upper.replace(' ', '_')
    }

    // ---- ID3v2 ----

    private val ID3_FRAMES = mapOf(
        "TIT2" to "TITLE", "TPE1" to "ARTIST", "TALB" to "ALBUM", "TPE2" to "ALBUMARTIST", "TRCK" to "TRACKNUMBER",
        "TPOS" to "DISCNUMBER", "TYER" to "DATE", "TDRC" to "DATE", "TCON" to "GENRE", "TCMP" to "COMPILATION",
        "TT2" to "TITLE", "TP1" to "ARTIST", "TAL" to "ALBUM", "TP2" to "ALBUMARTIST", "TRK" to "TRACKNUMBER",
        "TPA" to "DISCNUMBER", "TYE" to "DATE", "TCO" to "GENRE", "TCP" to "COMPILATION",
    )

    /** Reads an ID3v2 tag at the start of the file; returns where the audio starts. */
    private fun readId3(file: RandomRead, out: Tags): Long {
        val header = file.read(0, 10)
        if (header.size < 10) return 0
        val version = header[3].toInt()
        val flags = header[5].toInt() and 0xFF
        val size = synchsafe(header, 6)
        var body = file.read(10, size)
        if (flags and 0x80 != 0 && version < 4) body = unsync(body)
        var pos = 0
        // An extended header to skip.
        if (flags and 0x40 != 0 && body.size >= 4) {
            pos = if (version >= 4) synchsafe(body, 0) else int32(body, 0) + 4
        }
        val idLength = if (version == 2) 3 else 4
        val headerLength = if (version == 2) 6 else 10
        while (pos + headerLength <= body.size) {
            val id = String(body, pos, idLength, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = when (version) {
                2 -> ((body[pos + 3].toInt() and 0xFF) shl 16) or ((body[pos + 4].toInt() and 0xFF) shl 8) or (body[pos + 5].toInt() and 0xFF)
                3 -> int32(body, pos + 4)
                else -> synchsafe(body, pos + 4)
            }
            val frameFlags = if (version >= 3) ((body[pos + 8].toInt() and 0xFF) shl 8) or (body[pos + 9].toInt() and 0xFF) else 0
            val start = pos + headerLength
            if (frameSize <= 0 || start + frameSize > body.size) break
            var data = body.copyOfRange(start, start + frameSize)
            // ID3v2.4 frames can be unsynchronised one by one, and carry a data length in front.
            if (version >= 4) {
                if (frameFlags and 0x0001 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size)
                if (frameFlags and 0x0002 != 0) data = unsync(data)
            }
            val compressedOrEncrypted = if (version >= 4) frameFlags and 0x000C != 0 else frameFlags and 0x00C0 != 0
            if (!compressedOrEncrypted) id3Frame(id, data, out)
            pos = start + frameSize
        }
        val footer = if (version >= 4 && header[5].toInt() and 0x10 != 0) 10 else 0
        return 10L + size + footer
    }

    private fun id3Frame(id: String, data: ByteArray, out: Tags) {
        if (data.isEmpty()) return
        when {
            id == "TXXX" || id == "TXX" -> {
                val encoding = data[0].toInt()
                val (description, rest) = splitTerminated(data, 1, encoding)
                id3Values(data, rest, encoding).forEach { out.add(description, it) }
            }
            id == "UFID" || id == "UFI" -> {
                val (owner, rest) = splitTerminated(data, 0, 0)
                if (owner.equals("http://musicbrainz.org", ignoreCase = true) && rest < data.size) {
                    out.add("MUSICBRAINZ_TRACKID", String(data, rest, data.size - rest, Charsets.ISO_8859_1))
                }
            }
            id.startsWith("T") -> {
                val key = ID3_FRAMES[id] ?: return
                val values = id3Values(data, 1, data[0].toInt())
                values.forEach { v ->
                    // ID3v2.3 genres can be "(17)" or "(17)Rock": a number from the ID3v1 list, which isn't kept here.
                    val clean = if (key == "GENRE") v.replace(Regex("^\\(\\d+\\)"), "").ifBlank { v } else v
                    out.add(key, clean)
                }
            }
        }
    }

    /** The text values of a frame from [from], split on the NUL between values (ID3v2.4 keeps several that way). */
    internal fun id3Values(data: ByteArray, from: Int, encoding: Int): List<String> {
        if (from >= data.size) return emptyList()
        val text = decode(data, from, data.size - from, encoding)
        return text.split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Text up to its terminator (one or two NULs, by encoding) and where what follows starts. */
    private fun splitTerminated(data: ByteArray, from: Int, encoding: Int): Pair<String, Int> {
        val wide = encoding == 1 || encoding == 2
        var i = from
        while (i < data.size) {
            if (wide) {
                if (i + 1 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                    return decode(data, from, i - from, encoding) to i + 2
                }
                i += 2
            } else {
                if (data[i] == 0.toByte()) return decode(data, from, i - from, encoding) to i + 1
                i++
            }
        }
        return decode(data, from, data.size - from, encoding) to data.size
    }

    private fun decode(data: ByteArray, from: Int, length: Int, encoding: Int): String {
        if (length <= 0) return ""
        val charset: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        // UTF-16 values after the first each carry their own byte order mark.
        return String(data, from, length, charset).replace("﻿", "").replace("￾", "")
    }

    private fun unsync(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i] == 0xFF.toByte() && i + 1 < data.size && data[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    // ---- FLAC and Vorbis comments ----

    private fun readFlac(file: RandomRead, start: Long, out: Tags) {
        var pos = start
        while (pos + 4 <= file.size) {
            val header = file.read(pos, 4)
            if (header.size < 4) return
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            if (type == 4) {
                vorbisComment(file.read(pos + 4, length), 0, out)
                return
            }
            if (last) return
            pos += 4 + length
        }
    }

    /** A Vorbis comment block (little-endian lengths): vendor, then "KEY=value" entries. */
    internal fun vorbisComment(data: ByteArray, from: Int, out: Tags) {
        var pos = from
        if (pos + 4 > data.size) return
        val vendor = le32(data, pos)
        pos += 4 + vendor
        if (pos + 4 > data.size || vendor < 0) return
        val count = le32(data, pos)
        pos += 4
        repeat(count) {
            if (pos + 4 > data.size) return
            val length = le32(data, pos)
            pos += 4
            if (length < 0 || pos + length > data.size) return
            val entry = String(data, pos, length, Charsets.UTF_8)
            pos += length
            val eq = entry.indexOf('=')
            if (eq > 0) {
                val key = entry.substring(0, eq)
                // Pictures are big and read by Android; only text is kept.
                if (!key.equals("METADATA_BLOCK_PICTURE", ignoreCase = true) && !key.equals("COVERART", ignoreCase = true)) {
                    out.add(key, entry.substring(eq + 1))
                }
            }
        }
    }

    /**
     * Ogg Vorbis and Opus: the second packet of the first stream is the comment header ("\u0003vorbis" or
     * "OpusTags"), which can run over several pages when it holds a picture.
     */
    private fun readOgg(file: RandomRead, out: Tags) {
        var pos = 0L
        var packet = 0
        val current = java.io.ByteArrayOutputStream()
        while (pos + 27 <= file.size && packet < 2) {
            val header = file.read(pos, 27)
            if (!header.startsWith("OggS")) return
            val segments = header[26].toInt() and 0xFF
            val table = file.read(pos + 27, segments)
            var dataPos = pos + 27 + segments
            for (lace in table) {
                val n = lace.toInt() and 0xFF
                if (packet == 1) current.write(file.read(dataPos, n))
                dataPos += n
                if (n < 255) {
                    packet++
                    if (packet == 2) break
                }
                if (current.size() > MAX_COMMENT) return
            }
            pos = dataPos
        }
        val bytes = current.toByteArray()
        when {
            bytes.startsWith("\u0003vorbis") -> vorbisComment(bytes, 7, out)
            bytes.startsWith("OpusTags") -> vorbisComment(bytes, 8, out)
        }
    }

    // ---- MP4 ----

    private val MP4_ITEMS = mapOf(
        "©nam" to "TITLE", "©ART" to "ARTIST", "©alb" to "ALBUM", "aART" to "ALBUMARTIST", "©day" to "DATE",
        "©gen" to "GENRE", "trkn" to "TRACKNUMBER", "disk" to "DISCNUMBER", "cpil" to "COMPILATION",
    )

    private fun readMp4(file: RandomRead, out: Tags) {
        val moov = findBox(file, 0, file.size, "moov") ?: return
        val udta = findBox(file, moov.first, moov.second, "udta")
        // iTunes keeps "meta" in "udta"; some writers put it straight in "moov".
        val meta = udta?.let { findBox(file, it.first, it.second, "meta") } ?: findBox(file, moov.first, moov.second, "meta") ?: return
        // "meta" is a full box: four bytes of version and flags before its children (QuickTime's has none).
        val metaStart = if (file.read(meta.first + 4, 4).startsWith("hdlr")) meta.first else meta.first + 4
        val ilst = findBox(file, metaStart, meta.second, "ilst") ?: return
        val length = (ilst.second - ilst.first).toInt()
        if (length <= 0 || length > MAX_COMMENT) return
        val data = file.read(ilst.first, length)
        var pos = 0
        while (pos + 8 <= data.size) {
            val size = int32(data, pos)
            if (size < 8 || pos + size > data.size) break
            val name = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            mp4Item(name, data, pos + 8, pos + size, out)
            pos += size
        }
    }

    private fun mp4Item(name: String, data: ByteArray, from: Int, to: Int, out: Tags) {
        var freeformName: String? = null
        var pos = from
        while (pos + 8 <= to) {
            val size = int32(data, pos)
            if (size < 8 || pos + size > to) return
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            when (type) {
                "name" -> if (size > 12) freeformName = String(data, pos + 12, size - 12, Charsets.UTF_8)
                "data" -> if (size >= 16) {
                    val valueType = int32(data, pos + 8) and 0xFFFFFF
                    val value = data.copyOfRange(pos + 16, pos + size)
                    if (name == "----") {
                        freeformName?.let { key -> out.add(key, String(value, Charsets.UTF_8)) }
                    } else {
                        val key = MP4_ITEMS[name]
                        when {
                            key == null -> Unit
                            name == "trkn" || name == "disk" -> if (value.size >= 6) {
                                val n = ((value[2].toInt() and 0xFF) shl 8) or (value[3].toInt() and 0xFF)
                                val total = ((value[4].toInt() and 0xFF) shl 8) or (value[5].toInt() and 0xFF)
                                if (n > 0) out.add(key, if (total > 0) "$n/$total" else "$n")
                            }
                            name == "cpil" -> if (value.isNotEmpty()) out.add(key, (value.last().toInt() and 0xFF).toString())
                            valueType == 1 -> out.add(key, String(value, Charsets.UTF_8))
                        }
                    }
                }
            }
            pos += size
        }
    }

    /** The first box named [name] between [from] and [to]: where its contents start and end. */
    private fun findBox(file: RandomRead, from: Long, to: Long, name: String): Pair<Long, Long>? {
        var pos = from
        while (pos + 8 <= to) {
            val header = file.read(pos, 16)
            if (header.size < 8) return null
            var size = int32(header, 0).toLong() and 0xFFFFFFFFL
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (size == 1L && header.size >= 16) {
                size = java.nio.ByteBuffer.wrap(header, 8, 8).long
                headerSize = 16
            } else if (size == 0L) {
                size = to - pos
            }
            if (size < headerSize) return null
            if (type == name) return (pos + headerSize) to minOf(to, pos + size)
            pos += size
        }
        return null
    }

    // ---- Bytes ----

    private const val MAX_COMMENT = 16 * 1024 * 1024

    private fun ByteArray.startsWith(text: String): Boolean =
        size >= text.length && text.indices.all { this[it] == text[it].code.toByte() }

    private fun synchsafe(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0x7F) shl 21) or ((b[at + 1].toInt() and 0x7F) shl 14) or ((b[at + 2].toInt() and 0x7F) shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
