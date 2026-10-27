package com.blooddonation.spring.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT tests (no Spring context, no DB): pure ABO/Rh compatibility logic.
 * Run: mvn test -Dtest=BloodCompatibilityTest
 */
class BloodCompatibilityTest {

    @Test
    void oNegativeAcceptsOnlyONegative() {
        assertEquals(List.of("O-"), BloodDonationService.compatibleGroups("O-"));
    }

    @Test
    void oPositiveAcceptsONegativeAndOPositive() {
        assertEquals(List.of("O-", "O+"), BloodDonationService.compatibleGroups("O+"));
    }

    @Test
    void abPositiveIsUniversalRecipient() {
        List<String> groups = BloodDonationService.compatibleGroups("AB+");
        assertEquals(8, groups.size());
        assertTrue(groups.containsAll(List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")));
    }

    @Test
    void abNegativeAcceptsOnlyRhNegativeGroups() {
        assertEquals(List.of("O-", "A-", "B-", "AB-"), BloodDonationService.compatibleGroups("AB-"));
    }

    @Test
    void aPositiveCompatibility() {
        assertEquals(List.of("O-", "O+", "A-", "A+"), BloodDonationService.compatibleGroups("A+"));
    }

    @Test
    void bPositiveCompatibility() {
        assertEquals(List.of("O-", "O+", "B-", "B+"), BloodDonationService.compatibleGroups("B+"));
    }

    @Test
    void nullGroupReturnsEmpty() {
        assertTrue(BloodDonationService.compatibleGroups(null).isEmpty());
    }

    @Test
    void inputIsTrimmedAndCaseInsensitive() {
        assertEquals(BloodDonationService.compatibleGroups("A+"), BloodDonationService.compatibleGroups(" a+ "));
    }

    @Test
    void unknownGroupFallsBackToItself() {
        assertEquals(List.of("X"), BloodDonationService.compatibleGroups("X"));
    }
}
