package app.siansa

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.playback.PlaybackService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A stand-in for Android Auto in the emulator tests. Android Auto has no screen that can run in CI (its Desktop Head
 * Unit needs a phone with Android Auto set up), but all it knows of Siansa is what the playback service answers:
 * the lists it browses, and the song, its length, the position and the buttons of the media session. This asks the
 * real service the same questions the same way, with Android's own media browser and controller, as the car does.
 */
class CarConnection : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var browser: MediaBrowser

    /** The session as the car sees it: the song and its length, the position, the buttons. */
    lateinit var controller: MediaController
        private set

    fun open(): CarConnection {
        val done = CountDownLatch(1)
        var connected = false
        instrumentation.runOnMainSync {
            browser = MediaBrowser(context, ComponentName(context, PlaybackService::class.java), object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    controller = MediaController(context, browser.sessionToken)
                    connected = true
                    done.countDown()
                }
                override fun onConnectionFailed() = done.countDown()
            }, null)
            browser.connect()
        }
        check(done.await(30, TimeUnit.SECONDS) && connected) { "The playback service didn't take the car's connection" }
        return this
    }

    /** The id of the tree's root, which the car opens first. */
    val root: String get() = browser.root

    /** What's inside [parentId], exactly as it reached the car. */
    fun children(parentId: String): List<MediaBrowser.MediaItem> {
        val done = CountDownLatch(1)
        var result: List<MediaBrowser.MediaItem>? = null
        val callback = object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                result = children.toList()
                done.countDown()
            }
            override fun onError(parentId: String) = done.countDown()
        }
        instrumentation.runOnMainSync { browser.subscribe(parentId, callback) }
        done.await(60, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { browser.unsubscribe(parentId, callback) }
        return result ?: throw AssertionError("The car got no list for $parentId")
    }

    override fun close() = instrumentation.runOnMainSync { browser.disconnect() }
}
