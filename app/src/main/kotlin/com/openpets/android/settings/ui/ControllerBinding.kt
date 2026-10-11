package com.openpets.android.settings.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle

/** Lifecycle-safe adapter for controller listener APIs. */
class ControllerBinding<S>(
    private val lifecycle: Lifecycle,
    private val snapshot: () -> S,
    private val addListener: ((S) -> Unit) -> Unit,
    private val removeListener: ((S) -> Unit) -> Unit,
    private val refresh: (() -> Unit)? = null,
    private val stopTicker: () -> Unit = {},
    private val postToMain: (() -> Unit) -> Unit = { task ->
        if (Looper.myLooper() == Looper.getMainLooper()) task() else Handler(Looper.getMainLooper()).post(task)
    },
    private val isMainThread:()->Boolean={Looper.myLooper()==Looper.getMainLooper()},
    private val render: (S) -> Unit,
) {
    private val lock=Any()
    private var activeEpoch = 0L
    private var active = false
    private var storedListener: ((S)->Unit)?=null
    private var lastRenderedEpoch:Long=-1L

    fun start() {
        checkMainThread()
        val epoch:Long
        val listener:(S)->Unit
        synchronized(lock){if(active)return;active=true;epoch=++activeEpoch;listener={value->if(isCurrent(epoch))deliver(value,epoch)};storedListener=listener}
        addListener(listener)
        refresh?.invoke()
        val current=snapshot()
        val renderExplicit=synchronized(lock){if(!active||activeEpoch!=epoch||lastRenderedEpoch==epoch)false else true}
        if(renderExplicit)deliver(current,epoch)
    }

    fun stop() {
        checkMainThread()
        val listener=synchronized(lock){active=false;activeEpoch++;storedListener.also{storedListener=null}}
        if(listener!=null)removeListener(listener)
        stopTicker()
    }

    fun releaseView() { stop() }

    private fun isCurrent(epoch:Long)=synchronized(lock){active&&activeEpoch==epoch}
    private fun deliver(value: S,epoch:Long) {
        if(!isCurrent(epoch))return
        val task = Runnable {
            if(!isCurrent(epoch))return@Runnable
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { synchronized(lock){if(!active||activeEpoch!=epoch)return@Runnable};render(value);synchronized(lock){if(active&&activeEpoch==epoch)lastRenderedEpoch=epoch} }
        }
        postToMain { task.run() }
    }
    private fun checkMainThread(){check(isMainThread()){ "ControllerBinding lifecycle operations must run on the main thread" }}
}
