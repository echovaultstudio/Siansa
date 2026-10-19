package app.musix.log

import java.io.File
import java.util.UUID

/**
 * A file of log lines that never grows past a limit (issue #237): when the current part passes [maxBytes] it
 * becomes ".1", ".1" becomes ".2", and so on, keeping [parts] parts at most. Safe for several writers.
 * A write that fails (disk full, folder gone) is dropped instead of failing the caller.
 */
class RotatingFile(private val dir: File, private val name: String, private val ext: String, private val maxBytes: Long, private val parts: Int) {
    val current: File get() = File(dir, "$name.$ext")

    /** Every part, oldest first. */
    fun files(): List<File> = ((parts - 1) downTo 1).map { File(dir, "$name.$it.$ext") }.filter { it.exists() } +
        listOf(current).filter { it.exists() }

    @Synchronized
    fun append(text: String) {
        runCatching {
            dir.mkdirs()
            if (current.length() + text.length > maxBytes && current.length() > 0) rotate()
            current.appendText(text)
        }
    }

    private fun rotate() {
        File(dir, "$name.${parts - 1}.$ext").delete()
        for (i in parts - 2 downTo 1) File(dir, "$name.$i.$ext").takeIf { it.exists() }?.renameTo(File(dir, "$name.${i + 1}.$ext"))
        current.renameTo(File(dir, "$name.1.$ext"))
    }

    @Synchronized
    fun clear() = (files() + current).forEach { it.delete() }

    fun sizeBytes(): Long = files().sumOf { it.length() }
}

/** The last [capacity] items, oldest first; adding is cheap and safe from any thread. */
class RingBuffer<T>(private val capacity: Int) {
    private val items = ArrayDeque<T>(capacity)

    @Synchronized
    fun add(item: T) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(item)
    }

    @Synchronized
    fun snapshot(): List<T> = items.toList()

    @Synchronized
    fun size(): Int = items.size
}

/**
 * Which app run and which operation an event belongs to (issue #237). [session] is new at every app start.
 * [current] is the operation in progress: an id set with [start] for the song being started (so its buffering,
 * requests and errors can be read together), or one set for a block of work with [within].
 */
object Trace {
    val session: String = UUID.randomUUID().toString().take(8)

    private val local = ThreadLocal<String?>()

    @Volatile
    private var playback: String? = null

    /** A new playback operation (a song chosen or started); returns its id. */
    fun start(what: String): String = "$what-${UUID.randomUUID().toString().take(8)}".also { playback = it }

    fun current(): String? = local.get() ?: playback

    /** Runs [block] as the operation [id] on this thread. */
    fun <T> within(id: String, block: () -> T): T {
        val before = local.get()
        local.set(id)
        try {
            return block()
        } finally {
            local.set(before)
        }
    }
}
