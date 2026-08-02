package com.rootapp

import com.rootapp.ai.FoodExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodExtractorTest {
    @Test fun `pizza and pasta splits into two unhealthy meals`() {
        val meals = FoodExtractor.parse("I ate pizza and pasta today")
        assertEquals(2, meals.size)
        assertEquals(listOf("pizza", "pasta"), meals.map { it.food })
        assertTrue(meals.all { !it.healthy })
    }

    @Test fun `salad and grilled chicken are two healthy meals`() {
        val meals = FoodExtractor.parse("salad and grilled chicken")
        assertEquals(2, meals.size)
        assertEquals(listOf("salad", "grilled chicken"), meals.map { it.food })
        assertTrue(meals.all { it.healthy })
    }

    @Test fun `single healthy item works`() {
        val meals = FoodExtractor.parse("I had an apple")
        assertEquals(1, meals.size)
        assertEquals("apple", meals[0].food)
        assertTrue(meals[0].healthy)
    }

    @Test fun `single junk item is unhealthy`() {
        val meals = FoodExtractor.parse("burger")
        assertEquals(1, meals.size)
        assertEquals("burger", meals[0].food)
        assertFalse(meals[0].healthy)
    }

    @Test fun `commas ampersand and plus all split`() {
        val meals = FoodExtractor.parse("fries, soda & cake plus rice")
        assertEquals(listOf("fries", "soda", "cake", "rice"), meals.map { it.food })
        assertEquals(listOf(false, false, false, true), meals.map { it.healthy })
    }

    @Test fun `empty and blank input yields no meals`() {
        assertTrue(FoodExtractor.parse("").isEmpty())
        assertTrue(FoodExtractor.parse("   ").isEmpty())
    }
}
