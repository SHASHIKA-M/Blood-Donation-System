package com.blooddonation.spring.service;

import com.blooddonation.spring.model.Donor;
import com.blooddonation.spring.model.Request;
import com.blooddonation.spring.repository.AppointmentRepository;
import com.blooddonation.spring.repository.BloodUnitRepository;
import com.blooddonation.spring.repository.ContactMessageRepository;
import com.blooddonation.spring.repository.DeliveryCourierRepository;
import com.blooddonation.spring.repository.DeliveryOrderRepository;
import com.blooddonation.spring.repository.DeliveryVehicleRepository;
import com.blooddonation.spring.repository.DonorRepository;
import com.blooddonation.spring.repository.RequestRepository;
import com.blooddonation.spring.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT tests (Mockito, no DB): BloodDonationService validation + business rules.
 * Service collaborators are mocked; only the method under test runs for real.
 * Run: mvn test -Dtest=BloodDonationServiceTest
 */
@ExtendWith(MockitoExtension.class)
class BloodDonationServiceTest {

    @Mock DonorRepository donorRepository;
    @Mock RequestRepository requestRepository;
    @Mock ContactMessageRepository messageRepository;
    @Mock BloodUnitRepository bloodUnitRepository;
    @Mock DeliveryOrderRepository deliveryOrderRepository;
    @Mock DeliveryVehicleRepository vehicleRepository;
    @Mock DeliveryCourierRepository courierRepository;
    @Mock AuthService authService;
    @Mock UserAccountRepository userAccountRepository;
    @Mock AppointmentRepository appointmentRepository;

    @InjectMocks BloodDonationService service;

    private Donor validDonor() {
        Donor d = new Donor();
        d.setName("Aman Silva");
        d.setPhone("0771234567");
        d.setBloodGroup("B+");
        d.setCity("Colombo");
        return d;
    }

    private Request validRequest() {
        Request r = new Request();
        r.setPatientName("Kamal Jayawardena");
        r.setBloodGroup("A+");
        r.setComponent("Whole Blood");
        r.setCity("Colombo");
        r.setUnits(2);
        r.setContactPhone("0719876543");
        r.setNeededBy(LocalDateTime.now().plusDays(2));
        return r;
    }

    // ── registerDonor validation ──────────────────────────────────────────

    @Test
    void registerDonor_rejectsShortName() {
        Donor d = validDonor();
        d.setName("A");
        Exception ex = assertThrows(Exception.class, () -> service.registerDonor(d));
        assertTrue(ex.getMessage().toLowerCase().contains("full name"));
    }

    @Test
    void registerDonor_rejectsMissingCity() {
        Donor d = validDonor();
        d.setCity("  ");
        Exception ex = assertThrows(Exception.class, () -> service.registerDonor(d));
        assertTrue(ex.getMessage().toLowerCase().contains("city"));
    }

    @Test
    void registerDonor_rejectsInvalidPhone() {
        Donor d = validDonor();
        d.setPhone("123");
        Exception ex = assertThrows(Exception.class, () -> service.registerDonor(d));
        assertTrue(ex.getMessage().toLowerCase().contains("phone"));
    }

    @Test
    void registerDonor_rejectsDuplicatePhone() {
        when(donorRepository.existsByPhone("0771234567")).thenReturn(true);
        Exception ex = assertThrows(Exception.class, () -> service.registerDonor(validDonor()));
        assertTrue(ex.getMessage().toLowerCase().contains("already registered"));
    }

    @Test
    void registerDonor_normalisesPhoneAndProvisionsLogin() throws Exception {
        Donor d = validDonor();
        d.setPhone("(077) 123-4567");
        when(donorRepository.existsByPhone("0771234567")).thenReturn(false);
        when(donorRepository.save(any(Donor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authService.createDonorAccount(any(Donor.class))).thenReturn("aman silva");

        Donor saved = service.registerDonor(d);

        assertEquals("0771234567", saved.getPhone());
        verify(authService).createDonorAccount(saved);
    }

    // ── eligibility (120-day NBTS rule) ───────────────────────────────────

    @Test
    void eligibility_eligibleWhenNeverDonated() {
        Donor d = validDonor();
        d.setLastDonatedAt(null);
        when(donorRepository.findById("d1")).thenReturn(Optional.of(d));
        assertEquals("ELIGIBLE", service.checkDonorEligibility("d1"));
    }

    @Test
    void eligibility_waitWhenDonated10DaysAgo() {
        Donor d = validDonor();
        d.setLastDonatedAt(LocalDateTime.now().minusDays(10));
        when(donorRepository.findById("d1")).thenReturn(Optional.of(d));
        // 120-day gap - 10 elapsed = 110 remaining
        assertEquals("WAIT:110", service.checkDonorEligibility("d1"));
    }

    @Test
    void eligibility_notFoundForUnknownId() {
        when(donorRepository.findById("nope")).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", service.checkDonorEligibility("nope"));
    }

    // ── addDonation rules ─────────────────────────────────────────────────

    @Test
    void addDonation_rejectsFutureDate() {
        Donor d = validDonor();
        when(donorRepository.findById("d1")).thenReturn(Optional.of(d));
        Exception ex = assertThrows(Exception.class,
                () -> service.addDonation("d1", LocalDateTime.now().plusDays(1), "Colombo", "x"));
        assertTrue(ex.getMessage().toLowerCase().contains("future"));
    }

    @Test
    void addDonation_enforces120DayGap() {
        Donor d = validDonor();
        d.setLastDonatedAt(LocalDateTime.now().minusDays(5));
        when(donorRepository.findById("d1")).thenReturn(Optional.of(d));
        Exception ex = assertThrows(Exception.class,
                () -> service.addDonation("d1", LocalDateTime.now(), "Colombo", "x"));
        assertTrue(ex.getMessage().toLowerCase().contains("wait"));
    }

    @Test
    void addDonation_recordsHistoryAfterGap() throws Exception {
        Donor d = validDonor();
        d.setLastDonatedAt(LocalDateTime.now().minusDays(200));
        when(donorRepository.findById("d1")).thenReturn(Optional.of(d));
        when(donorRepository.save(any(Donor.class))).thenAnswer(inv -> inv.getArgument(0));

        Donor saved = service.addDonation("d1", LocalDateTime.now(), "Colombo Central", "camp");

        assertEquals(1, saved.getDonationHistory().size());
        assertNotNull(saved.getLastDonatedAt());
    }

    // ── request validation ────────────────────────────────────────────────

    @Test
    void createRequest_rejectsZeroUnits() {
        Request r = validRequest();
        r.setUnits(0);
        Exception ex = assertThrows(Exception.class, () -> service.createRequest(r));
        assertTrue(ex.getMessage().contains("1 and 10"));
    }

    @Test
    void createRequest_rejectsTooManyUnits() {
        Request r = validRequest();
        r.setUnits(11);
        Exception ex = assertThrows(Exception.class, () -> service.createRequest(r));
        assertTrue(ex.getMessage().contains("1 and 10"));
    }

    @Test
    void createRequest_rejectsBadPhone() {
        Request r = validRequest();
        r.setContactPhone("12");
        Exception ex = assertThrows(Exception.class, () -> service.createRequest(r));
        assertTrue(ex.getMessage().toLowerCase().contains("phone"));
    }

    @Test
    void createRequest_partialMatchWhenNoStock() throws Exception {
        when(bloodUnitRepository.findByStatusAndExpiresAtAfter(eq("Approved - Available"), any()))
                .thenReturn(List.of());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request saved = service.createRequest(validRequest());

        assertEquals("Partial Match", saved.getStatus());
    }

    // ── status transitions ────────────────────────────────────────────────

    @Test
    void approveRequest_rejectsDeliveredStatus() {
        Request r = validRequest();
        r.setStatus("Delivered");
        when(requestRepository.findById("r1")).thenReturn(Optional.of(r));
        Exception ex = assertThrows(Exception.class, () -> service.approveRequest("r1"));
        assertTrue(ex.getMessage().contains("Only Pending/Submitted"));
    }

    @Test
    void updateRequestStatus_rejectsUnknownStatus() {
        Request r = validRequest();
        when(requestRepository.findById("r1")).thenReturn(Optional.of(r));
        Exception ex = assertThrows(Exception.class, () -> service.updateRequestStatus("r1", "Flying"));
        assertTrue(ex.getMessage().contains("Invalid request status"));
    }
}
