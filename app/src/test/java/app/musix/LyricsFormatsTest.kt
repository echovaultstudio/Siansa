package app.musix

import app.musix.data.LyricLine
import app.musix.data.LyricWord
import app.musix.data.Lyrics
import app.musix.data.LyricsFormats
import app.musix.ui.sungWords
import app.musix.ui.sungLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lyrics in every format they come in: LRC, enhanced LRC, TTML, SRT/VTT and plain text. */
class LyricsFormatsTest {
    @Test fun lrcLinesWithHeadersAndSeveralTimes() {
        val l = LyricsFormats.parse("[ar:Someone]\n[ti:Song]\n[00:12.30]First line\n[00:15.00][01:15.00]Chorus\n[00:20.5]Second")!!
        assertTrue(l.synced)
        assertEquals(listOf(12_300L, 15_000L, 20_500L, 75_000L), l.lines.map { it.startMs })
        assertEquals(listOf("First line", "Chorus", "Second", "Chorus"), l.lines.map { it.text })
    }

    @Test fun lrcOffsetMakesLyricsComeSooner() {
        val l = LyricsFormats.parse("[offset:+500]\n[00:10.00]Line")!!
        assertEquals(9_500L, l.lines.single().startMs)
    }

    @Test fun lrcMillisecondsAndColonFractions() {
        assertEquals(1_234L, LyricsFormats.parse("[00:01.234]a")!!.lines.single().startMs)
        assertEquals(1_230L, LyricsFormats.parse("[00:01:23]a")!!.lines.single().startMs)
    }

    @Test fun enhancedLrcWordsAndSyllables() {
        val l = LyricsFormats.parse("[00:10.00]<00:10.00>Hel<00:10.40>lo <00:11.00>world")!!
        val line = l.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(10_000L, 10_400L, 11_000L), line.words.map { it.startMs })
        assertEquals(listOf("Hel", "lo ", "world"), line.words.map { it.text })
        assertTrue(l.wordTimed)
    }

    @Test fun syncedLinesFromTheServerStillGetTheirWordTimes() {
        val fromServer = Lyrics(true, listOf(LyricLine(5_000, "<00:05.00>One <00:05.50>two")))
        val line = LyricsFormats.normalize(fromServer).lines.single()
        assertEquals("One two", line.text)
        assertEquals(2, line.words.size)
    }

    @Test fun rawLrcSentAsPlainTextIsRead() {
        val plain = Lyrics(false, listOf(LyricLine(null, "[00:01.00]a"), LyricLine(null, "[00:02.00]b")))
        val l = LyricsFormats.normalize(plain)
        assertTrue(l.synced)
        assertEquals(listOf(1_000L, 2_000L), l.lines.map { it.startMs })
    }

    @Test fun ttmlLinesAndWords() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:01.000" end="00:03.000"><span begin="00:01.000">Hello</span> <span begin="00:01.600">there</span></p>
              <p begin="1:02:03.5" end="1:02:05">Plain line</p>
            </div></body></tt>
        """.trimIndent()
        val l = LyricsFormats.parse(ttml)!!
        assertEquals(listOf(1_000L, 3_723_500L), l.lines.map { it.startMs })
        assertEquals("Hello there", l.lines[0].text)
        assertEquals(listOf(1_000L, 1_600L), l.lines[0].words.map { it.startMs })
        assertEquals("Plain line", l.lines[1].text)
    }

    @Test fun ttmlClockValues() {
        assertEquals(12_300L, LyricsFormats.clock("12.3s"))
        assertEquals(500L, LyricsFormats.clock("500ms"))
        assertEquals(63_450L, LyricsFormats.clock("01:03.45"))
        assertNull(LyricsFormats.clock(""))
    }

    @Test fun srtAndVtt() {
        val srt = "1\n00:00:01,500 --> 00:00:03,000\nFirst\n\n2\n00:00:04,000 --> 00:00:05,000\n<i>Second</i> part\n"
        assertEquals(listOf(1_500L, 4_000L), LyricsFormats.parse(srt)!!.lines.map { it.startMs })
        assertEquals("Second part", LyricsFormats.parse(srt)!!.lines[1].text)
        val vtt = "WEBVTT\n\n00:02.250 --> 00:04.000\nHi\n"
        assertEquals(2_250L, LyricsFormats.parse(vtt)!!.lines.single().startMs)
    }

    @Test fun plainTextStaysPlain() {
        assertNull(LyricsFormats.parse("Just words\n[Chorus]\nMore words"))
        val plain = Lyrics(false, listOf(LyricLine(null, "Just words")))
        assertFalse(LyricsFormats.normalize(plain).synced)
    }

    @Test fun mostDetailedLyricsWin() {
        val plain = Lyrics(false, listOf(LyricLine(null, "a")))
        val synced = Lyrics(true, listOf(LyricLine(1, "a")))
        val words = LyricsFormats.parse("[00:01.00]<00:01.00>a <00:02.00>b")!!
        assertEquals(words, LyricsFormats.best(listOf("eng" to plain, "eng" to words, "eng" to synced)))
        assertEquals(synced, LyricsFormats.best(listOf("eng" to plain, "eng" to synced)))
        // Same detail: the phone's language first.
        val other = Lyrics(true, listOf(LyricLine(1, "b")))
        assertEquals(other, LyricsFormats.best(listOf("eng" to synced, "fi" to other), preferLang = "fi"))
    }

    @Test fun wordsSungSoFar() {
        val line = LyricsFormats.parse("[00:10.00]<00:10.00>a <00:11.00>b <00:12.00>c")!!.lines.single()
        assertEquals(0, sungWords(line, 9_000))
        assertEquals(2, sungWords(line, 11_500))
        assertEquals(3, sungWords(line, 20_000))
    }

    /** Issue #362: the fill glides through the word being sung instead of jumping a word at a time. */
    @Test fun sungLettersGlideThroughEachWord() {
        val line = LyricLine(10_000, "Hey you", listOf(LyricWord(10_000, "Hey "), LyricWord(11_000, "you")))
        assertEquals(0f, sungLetters(line, 9_000, 13_000), 0.001f)
        assertEquals(2f, sungLetters(line, 10_500, 13_000), 0.001f) // half of "Hey "
        assertEquals(4f, sungLetters(line, 11_000, 13_000), 0.001f) // "Hey " done
        // The last word lasts until the next line, at most 900 ms.
        assertEquals(4f + 1.5f, sungLetters(line, 11_450, 13_000), 0.001f)
        assertEquals(7f, sungLetters(line, 12_000, 13_000), 0.001f)
        assertEquals(4f + 1.5f, sungLetters(line, 11_250, 11_500), 0.001f)
    }
}
