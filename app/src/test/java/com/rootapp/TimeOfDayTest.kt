package com.rootapp

import com.rootapp.ui.theme.Sky
import com.rootapp.ui.theme.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOfDayTest {

    @Test fun `band boundaries map correctly`() {
        assertEquals(TimeOfDay.MIDNIGHT, Sky.fromHour(0))
        assertEquals(TimeOfDay.MIDNIGHT, Sky.fromHour(4))
        assertEquals(TimeOfDay.DAWN, Sky.fromHour(5))
        assertEquals(TimeOfDay.DAWN, Sky.fromHour(7))
        assertEquals(TimeOfDay.DAY, Sky.fromHour(8))
        assertEquals(TimeOfDay.DAY, Sky.fromHour(16))
        assertEquals(TimeOfDay.DUSK, Sky.fromHour(17))
        assertEquals(TimeOfDay.DUSK, Sky.fromHour(19))
        assertEquals(TimeOfDay.NIGHT, Sky.fromHour(20))
        assertEquals(TimeOfDay.NIGHT, Sky.fromHour(23))
    }

    @Test fun `hours normalise for overflow and negatives`() {
        assertEquals(Sky.fromHour(1), Sky.fromHour(25))   // 25 -> 1
        assertEquals(Sky.fromHour(23), Sky.fromHour(-1))  // -1 -> 23
        assertEquals(TimeOfDay.DAY, Sky.fromHour(8 + 24))
    }

    @Test fun `moon only at night and midnight`() {
        assertTrue(Sky.isMoon(TimeOfDay.NIGHT))
        assertTrue(Sky.isMoon(TimeOfDay.MIDNIGHT))
        assertFalse(Sky.isMoon(TimeOfDay.DAWN))
        assertFalse(Sky.isMoon(TimeOfDay.DAY))
        assertFalse(Sky.isMoon(TimeOfDay.DUSK))
    }
}
