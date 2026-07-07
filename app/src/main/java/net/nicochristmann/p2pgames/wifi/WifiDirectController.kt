package net.nicochristmann.p2pgames.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around [WifiP2pManager] that exposes peer discovery and
 * connection state as flows.
 *
 * The host calls [createGroup] so it always becomes the group owner; joiners
 * call [startDiscovery] and then [connect] on a discovered device. Once
 * [connectionInfo] reports groupFormed, the game socket is opened towards
 * [WifiP2pInfo.groupOwnerAddress].
 *
 * Runtime permissions (fine location on <= Android 12, NEARBY_WIFI_DEVICES on
 * Android 13+) are checked by the UI before any of these methods are invoked,
 * hence the MissingPermission suppressions.
 */
class WifiDirectController(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val mgr = manager ?: return
            val ch = channel ?: return
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isP2pEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    mgr.requestPeers(ch) { peerList ->
                        _peers.value = peerList.deviceList.toList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    mgr.requestConnectionInfo(ch) { info ->
                        _connectionInfo.value = if (info != null && info.groupFormed) info else null
                    }
                }
            }
        }
    }

    /** Registers for Wi-Fi P2P system broadcasts. Safe to call once at startup. */
    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    val isSupported: Boolean get() = manager != null && channel != null

    @SuppressLint("MissingPermission")
    fun startDiscovery(onFailure: (String) -> Unit = {}) {
        val mgr = manager ?: return onFailure("Wi-Fi Direct is not supported on this device")
        val ch = channel ?: return
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) = onFailure(reasonToText(reason))
        })
    }

    fun stopDiscovery() {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.stopPeerDiscovery(ch, null)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice, onFailure: (String) -> Unit = {}) {
        val mgr = manager ?: return onFailure("Wi-Fi Direct is not supported on this device")
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            // Strongly prefer the other side (the session host) as group owner.
            groupOwnerIntent = 0
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) = onFailure(reasonToText(reason))
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val mgr = manager ?: return onFailure("Wi-Fi Direct is not supported on this device")
        val ch = channel ?: return
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure(reasonToText(reason))
        })
    }

    fun removeGroup(onDone: () -> Unit = {}) {
        val mgr = manager ?: return onDone()
        val ch = channel ?: return onDone()
        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onDone()
            override fun onFailure(reason: Int) = onDone()
        })
    }

    fun clearPeers() {
        _peers.value = emptyList()
    }

    private fun reasonToText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device"
        WifiP2pManager.BUSY -> "Wi-Fi Direct is busy — is Wi-Fi (and location) turned on?"
        else -> "Wi-Fi Direct error — is Wi-Fi (and location) turned on?"
    }
}
