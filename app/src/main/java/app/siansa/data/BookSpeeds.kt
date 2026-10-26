package app.siansa.data

import android.content.Context

/** The speed each book plays at, remembered per book (issue #521); 1× until set. */
class BookSpeeds(context: Context) {
    private val prefs = context.getSharedPreferences("book_speeds", Context.MODE_PRIVATE)
    fun speedOf(bookId: String): Float = prefs.getFloat(bookId, 1f)
    fun remember(bookId: String, speed: Float) { prefs.edit().putFloat(bookId, speed).apply() }
}
