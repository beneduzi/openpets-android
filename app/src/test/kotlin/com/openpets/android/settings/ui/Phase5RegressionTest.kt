package com.openpets.android.settings.ui

import org.junit.Assert.*
import org.junit.Test

class Phase5RegressionTest {
    @Test fun timerCommandRearmsTickerWhenStillRunning(){assertTrue(shouldRunSessionTicker(true));assertFalse(shouldRunSessionTicker(false))}
    @Test fun voiceCallbacksRemainValidAcrossStopStart(){val epoch=CallbackEpoch();val first=epoch.advance();epoch.advance();val second=epoch.advance();assertFalse(epoch.isCurrent(first));assertTrue(epoch.isCurrent(second))}
    @Test fun workflowDependenciesCanBeReacquiredForEachView(){var acquired=0;repeat(2){assertNotNull(reacquireViewDependency{++acquired})};assertEquals(2,acquired)}
    @Test fun vocabularyLookupRequiresMatchingWordAndLanguageAndAlwaysClearsRejectedBusyState(){
        val old=LookupKey("A","fr");val current=LookupKey("B","fr");assertFalse(lookupKeyMatches(old,current));assertTrue(lookupKeyMatches(current,current))
        assertFalse(rejectedLookupBusy(true,false));assertTrue(rejectedLookupBusy(true,true))
    }
}
