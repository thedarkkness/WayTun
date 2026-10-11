package com.waytun.app.vpn.remote

import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Service-process side of the state bridge: hands out [binder] for [ACTION_OBSERVE_STATE] binds
 * and pushes every state change of [backend] to all registered UI-process clients. [scope] must
 * dispatch on the main thread, the same thread the incoming [Handler] runs on.
 */
class VpnStatePublisher(
    scope: CoroutineScope,
    backend: VpnBackend,
    reconnecting: StateFlow<String?> = MutableStateFlow(null)
) {

    private val clients = mutableListOf<Messenger>()
    private var latest: Bundle = stateBundle(backend.state.value, backend.activeTunnelId.value)

    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == MSG_REGISTER) {
                message.replyTo?.let { client ->
                    clients += client
                    send(client, latest)
                }
            }
            true
        }
    )

    val binder: IBinder get() = incoming.binder

    init {
        combine(backend.state, backend.activeTunnelId, reconnecting) { state, id, reconnectingId ->
            // While the session is restarting the tunnel after a failure, present that as
            // "connecting" to the tunnel it is restoring rather than flickering to "off".
            if (reconnectingId != null && state !is TunnelState.Connected) {
                stateBundle(TunnelState.Connecting, reconnectingId)
            } else {
                stateBundle(state, id)
            }
        }
            .onEach { bundle ->
                latest = bundle
                clients.toList().forEach { send(it, bundle) }
            }
            .launchIn(scope)
    }

    private fun send(client: Messenger, bundle: Bundle) {
        try {
            client.send(Message.obtain(null, MSG_STATE).apply { data = Bundle(bundle) })
        } catch (e: RemoteException) {
            clients -= client
        }
    }
}
