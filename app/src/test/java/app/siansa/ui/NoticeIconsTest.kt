package app.siansa.ui

import app.siansa.mix.NOTICE_ICONS
import app.siansa.mix.Notice
import app.siansa.mix.NoticeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The icons a notice of your own can have (the owner, 18 Nov: the Test notice showed the filter icon). */
class NoticeIconsTest {
    @Test fun everyIconOfferedHasAPictureAndAName() {
        assertEquals(NOTICE_ICONS, NoticeIcons.all.map { it.first })
        NOTICE_ICONS.forEach { key ->
            assertNotNull(key, NoticeIcons.of(key))
            assertNotNull(key, NoticeIcons.nameOf(key))
        }
        assertEquals(NOTICE_ICONS.size, NOTICE_ICONS.toSet().size)
    }

    @Test fun aNoticeShowsItsChosenIcon() {
        val fire = Notice(NoticeKind.CUSTOM, "custom:p:3", "3 Test", "pinned/p", "fire")
        assertEquals(NoticeIcons.of("fire"), noticeIcon(fire))
        assertEquals(NoticeIcons.of("album"), noticeIcon(fire.copy(icon = "album")))
        // Not the filter icon any more, except for one that can't be read.
        assertEquals(noticeIcon(NoticeKind.CUSTOM), noticeIcon(fire.copy(icon = "")))
        assertEquals(noticeIcon(NoticeKind.LIKED), noticeIcon(Notice(NoticeKind.LIKED, "liked", "Liked songs", "liked", "fire")))
    }
}
