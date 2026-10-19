package app.musix.ui

import androidx.compose.ui.unit.dp

/**
 * The spacing scale: every gap and padding in the app is one of these six steps, so all pages share one
 * rhythm (the spacing policy, issue #160). Pick by what the space is for, not by how it looks today:
 *
 * - [Tight] 4 dp: a title and its subtitle, an icon and the text right against it.
 * - [Small] 8 dp: between chips, pills and buttons in a row; inside small things (a chip, a badge).
 * - [Medium] 12 dp: between cards and tiles; an icon or cover and the text beside it; inside small cards.
 * - [Page] 16 dp: a page's left and right edges; inside cards and sheets; a heading to what it heads.
 * - [Section] 24 dp: between the sections of a page.
 * - [Large] 32 dp: around empty states and loading, and big headers.
 *
 * `SpacingPolicyTest` fails on a raw dp number in `padding`, `spacedBy`, `PaddingValues` or `Spacer` in the
 * UI code. Pixel-exact drawing that isn't spacing (a ring around a selected swatch, a hairline, a thumb's
 * offset) can say so with `// spacing: exact` on that line.
 */
object Space {
    val Tight = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Page = 16.dp
    val Section = 24.dp
    val Large = 32.dp
}
