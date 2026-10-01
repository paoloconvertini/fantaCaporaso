package com.fantasta.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetCompetitionCalculatorTest {
    @Test void countsPurchasesNotJustOpponents() {
        assertEquals(1, TargetCompetitionCalculator.capacity(110, 1, 1, 1, 100));
        assertEquals(2, TargetCompetitionCalculator.capacity(250, 2, 2, 1, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(250, 1, 1, 1, 100));
    }
    @Test void doesNotCountOtherRoleSlotsAsTargetSlots() {
        assertEquals(0, TargetCompetitionCalculator.capacity(250, 0, 8, 1, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(250, 1, 8, 1, 100));
    }
    @Test void reservesCreditsForAllOtherSlots() {
        assertEquals(0, TargetCompetitionCalculator.capacity(110, 1, 11, 1, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(111, 1, 11, 1, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(203, 2, 4, 1, 100));
        assertEquals(2, TargetCompetitionCalculator.capacity(204, 2, 4, 1, 100));
    }
    @Test void requiresStrictlyHigherBid() {
        assertEquals(0, TargetCompetitionCalculator.capacity(100, 1, 1, 1, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(101, 1, 1, 1, 100));
    }
    @Test void countsGoalkeepersAsOneDoor() {
        assertEquals(1, TargetCompetitionCalculator.capacity(250, 3, 3, 3, 100));
        assertEquals(0, TargetCompetitionCalculator.capacity(250, 2, 2, 3, 100));
        assertEquals(0, TargetCompetitionCalculator.capacity(104, 3, 7, 3, 100));
        assertEquals(1, TargetCompetitionCalculator.capacity(105, 3, 7, 3, 100));
    }
    @Test void handlesNoSpendableCredits() {
        assertEquals(0, TargetCompetitionCalculator.capacity(250, 8, 8, 1, 0));
    }
}
