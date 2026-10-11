package com.waytun.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.ui.tunnels.sendDisconnect
import com.waytun.app.ui.tunnels.startConnect
import com.waytun.app.vpn.Engine
import com.waytun.app.vpn.VpnBackends
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * `adb shell am broadcast -n com.waytun.app/.debug.DebugCommandReceiver -a <action> --es name <substring>`
 *  - `connect`  : starts the first tunnel whose name contains `name`, or the `--ei nth`-th subscription tunnel of `--es proto`
 *                 (e.g. HYSTERIA2); `--es engine` XRAY|SINGBOX overrides the engine
 *  - `disconnect`: stops every engine
 *  - `addsub`   : adds the subscription at `--es url`
 *  - `refreshsub`: refreshes every subscription
 *  - `delsub`   : deletes the subscription whose name contains `name`
 * Results go to logcat under the tag WayTunDebug.
 */
class DebugCommandReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun tunnels(): TunnelRepository
        fun subscriptions(): SubscriptionRepository
        fun backends(): VpnBackends
        fun obfuscation(): ObfuscationManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(context, deps, intent)
            } catch (e: Exception) {
                Log.e(TAG, "command failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, deps: Deps, intent: Intent) {
        val name = intent.getStringExtra("name").orEmpty()
        when (intent.action) {
            "connect" -> {
                val list = deps.tunnels().observeTunnels().first()
                val proto = intent.getStringExtra("proto")
                val nth = intent.getIntExtra("nth", 0)
                val tunnel = if (proto != null) {
                    list.filter { it.protocol.name == proto && it.subscriptionId != null }.getOrNull(nth)
                } else {
                    list.firstOrNull { it.name.contains(name, ignoreCase = true) }
                }
                if (tunnel == null) { Log.w(TAG, "connect: no tunnel matching '$name'"); return }
                val settings = deps.obfuscation().settings.value
                val engine = intent.getStringExtra("engine")?.let { runCatching { Engine.valueOf(it) }.getOrNull() }
                    ?: deps.backends().runtimeEngine(tunnel.protocol, settings)
                val running = deps.backends().all.filter { (_, b) -> b.state.value !is com.waytun.app.vpn.TunnelState.Disconnected }.map { it.first }
                running.forEach { sendDisconnect(context, it) }
                if (running.isNotEmpty()) { deps.backends().awaitStopped(running); kotlinx.coroutines.delay(400) }
                Log.i(TAG, "connect '${tunnel.name}' on $engine")
                startConnect(context, engine, tunnel.id, tunnel.name, settings, deps.obfuscation().profileForConnect())
            }
            "disconnect" -> {
                Engine.entries.forEach { sendDisconnect(context, it) }
                Log.i(TAG, "disconnect sent")
            }
            "addsub" -> {
                val url = intent.getStringExtra("url").orEmpty()
                val result = deps.subscriptions().add(url)
                Log.i(TAG, "addsub: ${result.fold({ "ok imported=${it.imported} skipped=${it.skipped}" }, { "failed ${it.message}" })}")
            }
            "refreshsub" -> {
                deps.subscriptions().refreshStale(emptySet())
                Log.i(TAG, "refreshsub done")
            }
            "delsub" -> {
                val subs = deps.subscriptions().observeSubscriptions().first()
                val sub = subs.firstOrNull { it.name.contains(name, ignoreCase = true) }
                if (sub == null) { Log.w(TAG, "delsub: none matching '$name'"); return }
                deps.subscriptions().delete(sub.id)
                Log.i(TAG, "delsub: deleted '${sub.name}'")
            }
            else -> Log.w(TAG, "unknown action ${intent.action}")
        }
    }

    private companion object { const val TAG = "WayTunDebug" }
}
