package vn.hongducdev.honortoolkit.feature.automation

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationAlarmsTest {

    private fun at(hour: Int, minute: Int, dayOffset: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }.timeInMillis

    @Test
    fun aTimeStillAheadFiresToday() {
        assertEquals(at(7, 30), AutomationAlarms.nextOccurrence(7, 30, now = at(7, 0)))
    }

    @Test
    fun aTimeThatAlreadyPassedRollsToTomorrow() {
        assertEquals(at(7, 30, dayOffset = 1), AutomationAlarms.nextOccurrence(7, 30, now = at(8, 0)))
    }

    @Test
    fun theExactMinuteCountsAsPassed() {
        assertEquals(at(7, 30, dayOffset = 1), AutomationAlarms.nextOccurrence(7, 30, now = at(7, 30)))
    }
}
