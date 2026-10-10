package com.openpets.android.companion

import org.junit.Assert.*
import org.junit.Test

class DeadlineEngineTest {
 @Test fun orderingAndNearest() { val e=DeadlineEngine(); val a=e.add("later",200); val b=e.add("soon",100); assertEquals(listOf(b,a),e.upcoming(0)); assertEquals(b,e.nearest(0)) }
 @Test fun skipsPastOffsetsAndChoosesFifteenMinutes() { val now=10_000_000L; val e=DeadlineEngine(); e.add("soon",now+20*60_000); assertEquals(now+5*60_000,e.nextReminderAt(now)); assertEquals("15 minutes",e.reminderOffsetFor(now+20*60_000,now+5*60_000)) }
 @Test fun completeAndRemove() { val e=DeadlineEngine(); val a=e.add("a",1); val b=e.add("b",2); e.complete(a.id); assertEquals(listOf(b),e.upcoming(0)); e.remove(b.id); assertTrue(e.upcoming(0).isEmpty()) }
 @Test fun validatesTitles() { val e=DeadlineEngine(); assertEquals("ok",e.add("  ok ",1).title); assertThrows(IllegalArgumentException::class.java) { e.add(" ",1) }; assertThrows(IllegalArgumentException::class.java) { e.add("x".repeat(121),1) } }
}
