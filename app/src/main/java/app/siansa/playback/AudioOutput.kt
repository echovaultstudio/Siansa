package app.siansa.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the phone's own sound goes, when it isn't its speaker: Bluetooth or wired headphones. */
data class OutputDevice(val name: String, val bluetooth: Boolean)

/**
 * Follows the phone's audio outputs, so now playing can show "Sony Headphones" next to the device button
 * as Spotify does. Only names are kept; nothing about the device is logged beyond its kind.
 */
object AudioOutput {
    private val _device = MutableStateFlow<OutputDevice?>(null)
    val device: StateFlow<OutputDevice?> = _device.asStateFlow()
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val audio = context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        fun refresh() {
            val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type to it.productName?.toString() }
            val picked = pick(outputs)
            if (picked != _device.value) {
                AppLog.i("Output", picked?.let { if (it.bluetooth) "Playing through Bluetooth" else "Playing through headphones" } ?: "Playing through this phone")
            }
            _device.value = picked
        }
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    private const val TYPE_BLE_HEADSET = 26 // AudioDeviceInfo.TYPE_BLE_HEADSET (Android 12)
    private const val TYPE_BLE_SPEAKER = 27 // AudioDeviceInfo.TYPE_BLE_SPEAKER (Android 12)

    private val BLUETOOTH = listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER)
    private val WIRED = listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)

    /**
     * Picks the output music plays through from (type, name) pairs: Bluetooth first (as Android does),
     * then wired headphones; null for the phone's speaker. A Bluetooth device without a name is "Bluetooth".
     */
    fun pick(outputs: List<Pair<Int, String?>>): OutputDevice? {
        outputs.firstOrNull { it.first in BLUETOOTH }?.let { (_, name) ->
            return OutputDevice(name?.trim()?.takeIf { it.isNotEmpty() } ?: "Bluetooth", bluetooth = true)
        }
        if (outputs.any { it.first in WIRED }) return OutputDevice("Headphones", bluetooth = false)
        return null
    }
}
