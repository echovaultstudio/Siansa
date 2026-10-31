package app.siansa.ui

import app.siansa.ui.AnimatedCovers.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a big animated GIF cover becomes, from the server's answer for its small version (issue #736). */
class AnimatedCoverChoiceTest {
    private fun choice(answered: Boolean, animated: Boolean = false, shrunk: Boolean = false) =
        AnimatedCovers.bigAnimationChoice(answered, animated, shrunk)

    @Test fun aShrunkAnimationIsUsedAndKept() {
        assertEquals(Kind.Animated(SMALL_COVER) to true, choice(answered = true, animated = true, shrunk = true))
    }

    @Test fun anAnimationTheServerDoesntShrinkIsKeptOnThePhone() {
        assertEquals(Kind.Local to true, choice(answered = true, animated = true, shrunk = false))
    }

    @Test fun aSmallVersionThatDoesntMoveIsStillForGood() {
        assertEquals(Kind.StillOnlySmall to true, choice(answered = true, animated = false))
    }

    @Test fun noAnswerInTimeIsStillOnlyUntilTheNextStart() {
        // The server makes the small version slowly the first time; the next start asks again.
        assertEquals(Kind.StillOnlySmall to false, choice(answered = false))
    }
}
