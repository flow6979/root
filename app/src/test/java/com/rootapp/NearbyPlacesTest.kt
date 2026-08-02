package com.rootapp

import com.rootapp.location.NearbyPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NearbyPlacesTest {
    @Test fun `default no-results message uses widest radius in km`() {
        assertEquals("No eating spots found within 8km", NearbyPlaces.noResultsMessage())
    }

    @Test fun `whole-km radius renders without decimals`() {
        assertEquals("No eating spots found within 3km", NearbyPlaces.noResultsMessage(3000))
    }

    @Test fun `fractional km radius keeps one decimal`() {
        assertEquals("No eating spots found within 1.5km", NearbyPlaces.noResultsMessage(1500))
    }

    @Test fun `message never contains an em-dash`() {
        assertFalse(NearbyPlaces.noResultsMessage().contains("—"))
    }

    @Test fun `place kind classification is stable for old recorded data`() {
        val junk = NearbyPlaces.Place("Burger Hut", 0.0, 0.0, 100, "fast_food")
        val healthy = NearbyPlaces.Place("Green Bowl", 0.0, 0.0, 100, "restaurant")
        assertEquals(true, junk.isJunk)
        assertEquals("fast food", junk.healthLabel)
        assertEquals(false, healthy.isJunk)
        assertEquals("restaurant", healthy.healthLabel)
    }
}
