package com.openpets.android.settings.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ControllerBindingTest {
    private class FakeController {
        val listeners=CopyOnWriteArrayList<(Int)->Unit>()
        val retired=mutableListOf<(Int)->Unit>()
        var adds=0;var removes=0;var value=7
        fun add(l:(Int)->Unit){adds++;listeners+=l}
        fun remove(l:(Int)->Unit){removes++;listeners.remove(l);retired+=l}
        fun emit(v:Int){value=v;listeners.toList().forEach{it(v)}}
        fun emitRetired(v:Int)=retired.last()(v)
    }
    private fun lifecycle(state:Lifecycle.State=Lifecycle.State.STARTED):LifecycleRegistry {
        val registry=LifecycleRegistry.createUnsafe(object:androidx.lifecycle.LifecycleOwner{override val lifecycle:Lifecycle get()=registryRef})
        registryRef=registry
        registry.currentState=state
        return registry
    }
    private lateinit var registryRef:LifecycleRegistry
    private fun make(fake:FakeController,lc:LifecycleRegistry,rendered:MutableList<Int>,queue:MutableList<()->Unit> = mutableListOf())=
        ControllerBinding(lc,{fake.value},{fake.add(it)},{fake.remove(it)},postToMain={queue+=it},isMainThread={true},render={rendered+=it})

    @Test fun repeatedStartRegistersOnceAndStopInvalidatesQueuedDelivery(){
        val lc=lifecycle();val fake=FakeController();val shown=mutableListOf<Int>();val queue=mutableListOf<()->Unit>();val b=make(fake,lc,shown,queue)
        b.start();b.start();assertEquals(1,fake.adds)
        fake.emit(8);b.stop();queue.toList().forEach{it()}
        assertEquals(emptyList<Int>(),shown);assertEquals(1,fake.removes)
    }
    @Test fun oldRegistrationCannotRenderAfterRestartAndReleaseInvalidatesQueue(){
        val lc=lifecycle();val fake=FakeController();val shown=mutableListOf<Int>();val queue=mutableListOf<()->Unit>();val b=make(fake,lc,shown,queue)
        b.start();queue.toList().forEach{it()};queue.clear();shown.clear();b.stop();b.start();queue.toList().forEach{it()};queue.clear();shown.clear()
        fake.emitRetired(11);assertTrue(queue.isEmpty());assertTrue(shown.isEmpty());assertEquals(2,fake.adds)
        fake.emit(12);b.releaseView();queue.toList().forEach{it()};assertTrue(shown.isEmpty())
    }
    @Test fun inactiveAndDestroyedLifecycleNeverRender(){
        val lc=lifecycle(Lifecycle.State.CREATED);val fake=FakeController();val shown=mutableListOf<Int>();val queue=mutableListOf<()->Unit>();val b=make(fake,lc,shown,queue)
        b.start();queue.toList().forEach{it()};assertTrue(shown.isEmpty())
        lc.currentState=Lifecycle.State.DESTROYED;fake.emit(9);queue.toList().forEach{it()};assertTrue(shown.isEmpty());b.stop()
    }
    @Test fun workerEmissionIsQueuedForMainDelivery(){
        val lc=lifecycle();val fake=FakeController();val shown=mutableListOf<Int>();val queue=mutableListOf<()->Unit>();val b=make(fake,lc,shown,queue)
        b.start();queue.clear();Thread{fake.emit(44)}.apply{start();join()}
        assertTrue(shown.isEmpty());assertFalse(queue.isEmpty());queue.toList().forEach{it()};assertEquals(listOf(44),shown)
    }
}
