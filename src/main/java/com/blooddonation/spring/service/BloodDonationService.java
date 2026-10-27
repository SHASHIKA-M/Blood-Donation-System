package com.blooddonation.spring.service;

import com.blooddonation.spring.model.*;
import com.blooddonation.spring.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class BloodDonationService {

    private static final Logger log = LoggerFactory.getLogger(BloodDonationService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** Minimum gap between whole-blood donations (NBTS guideline: 120 days). */
    public static final long MIN_DONATION_GAP_DAYS = 120;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ContactMessageRepository messageRepository;

    @Autowired
    private BloodUnitRepository bloodUnitRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private DeliveryVehicleRepository vehicleRepository;

    @Autowired
    private DeliveryCourierRepository courierRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int CRITICAL_STOCK_THRESHOLD = 5;

    /**
     * Real ABO/Rh compatibility: recipient group → acceptable donor groups
     * (red-cell transfusion rules; O- universal donor, AB+ universal recipient).
     */
    public static final java.util.Map<String, java.util.List<String>> COMPATIBLE_DONORS = java.util.Map.of(
        "O-", java.util.List.of("O-"),
        "O+", java.util.List.of("O-", "O+"),
        "A-", java.util.List.of("O-", "A-"),
        "A+", java.util.List.of("O-", "O+", "A-", "A+"),
        "B-", java.util.List.of("O-", "B-"),
        "B+", java.util.List.of("O-", "O+", "B-", "B+"),
        "AB-", java.util.List.of("O-", "A-", "B-", "AB-"),
        "AB+", java.util.List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")
    );

    public static List<String> compatibleGroups(String recipientGroup) {
        if (recipientGroup == null) return List.of();
        return COMPATIBLE_DONORS.getOrDefault(recipientGroup.trim().toUpperCase(), List.of(recipientGroup));
    }

    // ─── Seed initial data if DB is empty ────────────────────────────────────
    @PostConstruct
    public void seedInitialData() {
        if (!seedEnabled) {
            log.info("Demo data seeding disabled (app.seed.enabled=false)");
            return;
        }
        if (donorRepository.count() == 0) {
            // Seed Donor 1
            Donor d1 = new Donor();
            d1.setName("Aman Silva");
            d1.setPhone("0771234567");
            d1.setBloodGroup("B+");
            d1.setCity("Colombo");
            d1.setProvince("Western Province");
            d1.setNotes("Available for donations on weekends");
            d1.setAvailability("Available");
            d1.setAllowCall(true);
            d1.setLastDonatedAt(LocalDateTime.of(2026, 5, 10, 10, 0));
            d1 = donorRepository.save(d1);

            AvailabilitySlot s1a = new AvailabilitySlot("Sat", "09:00", "13:00");
            s1a.setDonor(d1);
            AvailabilitySlot s1b = new AvailabilitySlot("Sun", "14:00", "18:00");
            s1b.setDonor(d1);
            d1.setAvailabilitySlots(Arrays.asList(s1a, s1b));

            DonationHistory h1 = new DonationHistory(
                    LocalDateTime.of(2026, 5, 10, 10, 0),
                    "National Blood Center Colombo",
                    "Annual blood camp");
            h1.setDonor(d1);
            d1.setDonationHistory(Arrays.asList(h1));
            donorRepository.save(d1);

            // Seed Donor 2
            Donor d2 = new Donor();
            d2.setName("Priya Perera");
            d2.setPhone("0712345678");
            d2.setBloodGroup("O-");
            d2.setCity("Kandy");
            d2.setProvince("Central Province");
            d2.setNotes("Universal donor");
            d2.setAvailability("Available");
            d2.setAllowCall(true);
            d2 = donorRepository.save(d2);

            AvailabilitySlot s2a = new AvailabilitySlot("Mon", "08:00", "10:00");
            s2a.setDonor(d2);
            AvailabilitySlot s2b = new AvailabilitySlot("Wed", "16:00", "18:00");
            s2b.setDonor(d2);
            d2.setAvailabilitySlots(Arrays.asList(s2a, s2b));
            donorRepository.save(d2);

            // Seed Donor 3
            Donor d3 = new Donor();
            d3.setName("Dilshan Fernando");
            d3.setPhone("0753456789");
            d3.setBloodGroup("A+");
            d3.setCity("Galle");
            d3.setProvince("Southern Province");
            d3.setNotes("Recently donated");
            d3.setAvailability("Unavailable");
            d3.setAllowCall(false);
            d3.setLastDonatedAt(LocalDateTime.of(2026, 7, 20, 8, 0));
            d3 = donorRepository.save(d3);

            DonationHistory h3 = new DonationHistory(
                    LocalDateTime.of(2026, 7, 20, 8, 0),
                    "Teaching Hospital Karapitiya",
                    "First donation");
            h3.setDonor(d3);
            d3.setDonationHistory(Arrays.asList(h3));
            donorRepository.save(d3);
        }

        if (requestRepository.count() == 0) {
            Request r1 = new Request();
            r1.setPatientName("Kamal Jayawardena");
            r1.setBloodGroup("A+");
            r1.setCity("Colombo");
            r1.setHospital("Apollo Hospital");
            r1.setUnits(2);
            r1.setContactPhone("0719876543");
            r1.setNeededBy(LocalDateTime.of(2026, 8, 5, 12, 0));
            r1.setNotes("Urgent CABG surgery scheduled. Please support.");
            r1.setUrgency("Urgent");
            r1.setStatus("Pending");
            requestRepository.save(r1);

            Request r2 = new Request();
            r2.setPatientName("Ruwan Kumara");
            r2.setBloodGroup("O-");
            r2.setCity("Kandy");
            r2.setHospital("General Hospital Kandy");
            r2.setUnits(1);
            r2.setContactPhone("0777654321");
            r2.setNeededBy(LocalDateTime.of(2026, 7, 25, 15, 0));
            r2.setNotes("Needed for emergency dengue patient. Resolved.");
            r2.setStatus("Delivered");
            requestRepository.save(r2);
        }

        if (messageRepository.count() == 0) {
            ContactMessage m1 = new ContactMessage(
                    "Lasitha Bandara",
                    "lasitha@gmail.com",
                    "Inquiry about upcoming blood camp",
                    "Hello, I would like to know if there is any upcoming blood donation camp in Colombo next month. Thanks!");
            messageRepository.save(m1);
        }
        if (bloodUnitRepository.count() == 0) {
            for (int i = 0; i < 15; i++) seedBloodUnit("A+", "Whole Blood", "Colombo Central", 35);
            for (int i = 0; i < 8; i++) seedBloodUnit("O-", "Whole Blood", "Colombo Central", 28);
            for (int i = 0; i < 4; i++) seedBloodUnit("B+", "Plasma", "Kandy Branch", 90);
            for (int i = 0; i < 3; i++) seedBloodUnit("AB-", "Platelets", "Galle Branch", 5);
            seedBloodUnit("A+", "Whole Blood", "Kandy Branch", 35);
            seedBloodUnit("O+", "Red Cells", "Colombo Central", 20);
            BloodUnit pending = new BloodUnit();
            pending.setBloodGroup("B+"); pending.setComponent("Whole Blood"); pending.setBranch("Colombo Central");
            pending.setExpiresAt(LocalDateTime.now().plusDays(30)); pending.setStatus("Pending Screening");
            bloodUnitRepository.save(pending);
        }
        if (vehicleRepository.count() == 0) {
            DeliveryVehicle v1 = new DeliveryVehicle();
            v1.setVehicleNumber("WP-CAB-1234"); v1.setType("Refrigerated Van"); v1.setRouteZone("Western Province");
            vehicleRepository.save(v1);
            DeliveryVehicle v2 = new DeliveryVehicle();
            v2.setVehicleNumber("CP-TRK-5678"); v2.setType("Cold Chain Truck"); v2.setRouteZone("Central Province");
            vehicleRepository.save(v2);
        }
        if (courierRepository.count() == 0) {
            DeliveryCourier c1 = new DeliveryCourier();
            c1.setName("Nimal Perera"); c1.setLicenseNumber("DL-123456"); c1.setContact("0771112233"); c1.setRouteZone("Western Province");
            courierRepository.save(c1);
            DeliveryCourier c2 = new DeliveryCourier();
            c2.setName("Sunil Fernando"); c2.setLicenseNumber("DL-789012"); c2.setContact("0774445566"); c2.setRouteZone("Central Province");
            courierRepository.save(c2);
        }
        // Backfill: donors registered before auto-login existed get accounts too
        // (username = name, password = phone number)
        for (Donor d : donorRepository.findAll()) {
            try {
                authService.createDonorAccount(d);
            } catch (Exception e) {
                log.warn("Backfill skipped for donor {}: {}", d.getId(), e.getMessage());
            }
        }
    }

    private void seedBloodUnit(String group, String component, String branch, int expiryDays) {
        BloodUnit unit = new BloodUnit();
        unit.setBloodGroup(group); unit.setComponent(component); unit.setBranch(branch);
        unit.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
        unit.setHivNonReactive(true); unit.setHepatitisBNonReactive(true); unit.setHepatitisCNonReactive(true);
        unit.setSyphilisNonReactive(true); unit.setMalariaNonReactive(true); unit.setStatus("Approved - Available");
        unit.setScreenedAt(LocalDateTime.now()); bloodUnitRepository.save(unit);
    }

    @Transactional(readOnly = true)
    public List<BloodUnit> getBloodUnits(String group) {
        return bloodUnitRepository.findAll().stream()
                .filter(unit -> group == null || group.isBlank() || "Any".equalsIgnoreCase(group) || unit.getBloodGroup().equalsIgnoreCase(group))
                .sorted(Comparator.comparing(BloodUnit::getExpiresAt)).limit(500).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countAvailableUnits(String bloodGroup, String component, String branch) {
        // Normalise: blank/"Any"/"All" means no filter; trim to survive URL-decoding quirks ("A " -> "A+").
        // NB: query params arrive URL-decoded, so "+" must be encoded client-side (encodeURIComponent).
        String group = (bloodGroup == null || bloodGroup.isBlank()
                || "Any".equalsIgnoreCase(bloodGroup.trim()) || "All".equalsIgnoreCase(bloodGroup.trim()))
                ? null : bloodGroup.trim();
        String comp = (component == null || component.isBlank()
                || "Any".equalsIgnoreCase(component.trim()) || "All".equalsIgnoreCase(component.trim()))
                ? null : component.trim();
        String br = (branch == null || branch.isBlank()) ? null : branch.trim();
        java.time.LocalDateTime now = LocalDateTime.now();

        // True DB-level counts (no findAll().stream() — works on real data volume).
        if (br != null) {
            // Branch filter is low-cardinality; keep the null-safe in-memory path for it.
            return bloodUnitRepository.findAll().stream()
                    .filter(u -> "Approved - Available".equals(u.getStatus()))
                    .filter(u -> u.getExpiresAt() != null && u.getExpiresAt().isAfter(now))
                    .filter(u -> group == null || (u.getBloodGroup() != null && u.getBloodGroup().equalsIgnoreCase(group)))
                    .filter(u -> comp == null || (u.getComponent() != null && u.getComponent().equalsIgnoreCase(comp)))
                    .filter(u -> u.getBranch() != null && u.getBranch().equalsIgnoreCase(br))
                    .count();
        }
        if (group != null && comp != null) {
            return bloodUnitRepository.countByStatusAndBloodGroupIgnoreCaseAndComponentIgnoreCaseAndExpiresAtAfter(
                    "Approved - Available", group, comp, now);
        }
        if (group != null) {
            return bloodUnitRepository.countByStatusAndBloodGroupIgnoreCaseAndExpiresAtAfter(
                    "Approved - Available", group, now);
        }
        if (comp != null) {
            return bloodUnitRepository.countByStatusAndComponentIgnoreCaseAndExpiresAtAfter(
                    "Approved - Available", comp, now);
        }
        return bloodUnitRepository.countByStatusAndExpiresAtAfter("Approved - Available", now);
    }

    @Transactional(readOnly = true)
    public List<StockSummary> getStockDashboard(String branchFilter) {
        return bloodUnitRepository.findAll().stream()
                .filter(u -> "Approved - Available".equals(u.getStatus()))
                .filter(u -> u.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(u -> branchFilter == null || branchFilter.isBlank() || u.getBranch().equalsIgnoreCase(branchFilter))
                .collect(Collectors.groupingBy(u -> u.getBloodGroup() + "|" + u.getComponent() + "|" + u.getBranch()))
                .entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    long count = e.getValue().size();
                    String status = count <= CRITICAL_STOCK_THRESHOLD ? "Critical" : count <= LOW_STOCK_THRESHOLD ? "Low" : "Adequate";
                    return new StockSummary(parts[0], parts[1], parts[2], count, status);
                })
                .sorted(Comparator.comparing(StockSummary::getBloodGroup).thenComparing(StockSummary::getBranch))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockSummary> getLowStockAlerts() {
        return getStockDashboard(null).stream()
                .filter(s -> "Low".equals(s.getStatus()) || "Critical".equals(s.getStatus()))
                .collect(Collectors.toList());
    }

    public BloodUnit registerBloodUnit(BloodUnit unit) throws Exception {
        if (unit.getBloodGroup() == null || unit.getBloodGroup().isBlank() || unit.getExpiresAt() == null) throw new Exception("Blood group and expiry date are required");
        unit.setStatus("Pending Screening"); return bloodUnitRepository.save(unit);
    }

    public BloodUnit recordScreening(String id, boolean hiv, boolean hepB, boolean hepC, boolean syphilis, boolean malaria, String notes) throws Exception {
        BloodUnit unit = bloodUnitRepository.findById(id).orElseThrow(() -> new Exception("Blood unit not found"));
        unit.setHivNonReactive(hiv); unit.setHepatitisBNonReactive(hepB); unit.setHepatitisCNonReactive(hepC);
        unit.setSyphilisNonReactive(syphilis); unit.setMalariaNonReactive(malaria); unit.setQualityNotes(notes); unit.setScreenedAt(LocalDateTime.now());
        unit.setStatus(hiv && hepB && hepC && syphilis && malaria && unit.getExpiresAt().isAfter(LocalDateTime.now()) ? "Approved - Available" : "Rejected - Non-Compliant");
        return bloodUnitRepository.save(unit);
    }

    // ─── Donor operations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Donor> searchDonors(String q, String bloodGroup, String city) {
        return searchDonors(q, bloodGroup, city, null);
    }

    @Transactional(readOnly = true)
    public List<Donor> searchDonors(String q, String bloodGroup, String city, String province) {
        List<Donor> all = donorRepository.findAll();
        return all.stream()
            .filter(d -> {
                if (bloodGroup != null && !bloodGroup.trim().isEmpty() && !bloodGroup.equalsIgnoreCase("Any") && !bloodGroup.equalsIgnoreCase("All")) {
                    if (!d.getBloodGroup().equalsIgnoreCase(bloodGroup.trim())) return false;
                }
                if (city != null && !city.trim().isEmpty()) {
                    if (d.getCity() == null || !d.getCity().toLowerCase().contains(city.toLowerCase().trim()))
                        return false;
                }
                if (province != null && !province.trim().isEmpty() && !province.equalsIgnoreCase("Any") && !province.equalsIgnoreCase("All")) {
                    boolean provinceMatch = d.getProvince() != null && d.getProvince().toLowerCase().contains(province.toLowerCase().trim());
                    boolean cityOrNotesMatch = (d.getCity() != null && d.getCity().toLowerCase().contains(province.toLowerCase().trim()))
                            || (d.getNotes() != null && d.getNotes().toLowerCase().contains(province.toLowerCase().trim()));
                    if (!provinceMatch && !cityOrNotesMatch) return false;
                }
                if (q != null && !q.trim().isEmpty()) {
                    String query = q.toLowerCase().trim();
                    boolean matchName = d.getName() != null && d.getName().toLowerCase().contains(query);
                    boolean matchPhone = d.getPhone() != null && d.getPhone().contains(query);
                    return matchName || matchPhone;
                }
                return true;
            })
            .sorted((d1, d2) -> {
                LocalDateTime c1 = d1.getCreatedAt() != null ? d1.getCreatedAt() : LocalDateTime.MIN;
                LocalDateTime c2 = d2.getCreatedAt() != null ? d2.getCreatedAt() : LocalDateTime.MIN;
                return c2.compareTo(c1);
            })
            .limit(200)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<Donor> getDonorById(String id) {
        return donorRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Request> getRequestById(String id) {
        return requestRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Donor> getDonorByPhone(String phone) {
        String cleanPhone = phone.replaceAll("\\D", "");
        return donorRepository.findByPhone(cleanPhone);
    }

    public Donor registerDonor(Donor donor) throws Exception {
        if (donor.getName() == null || donor.getName().trim().length() < 2) {
            throw new Exception("Please enter your full name (minimum 2 characters).");
        }
        if (donor.getCity() == null || donor.getCity().trim().isEmpty()) {
            throw new Exception("Please enter your city.");
        }
        String cleanPhone = donor.getPhone() != null ? donor.getPhone().replaceAll("\\D", "") : "";
        if (cleanPhone.length() < 9 || cleanPhone.length() > 15) {
            throw new Exception("Please enter a valid phone number (9 to 15 digits).");
        }
        donor.setPhone(cleanPhone);

        if (donor.getId() == null || donor.getId().isEmpty()) {
            if (donorRepository.existsByPhone(cleanPhone)) {
                throw new Exception("This phone number is already registered. Please search for your profile or use a different number.");
            }
        }
        // Real-world fix: sync bidirectional children so FK (donor_id) is never NULL
        if (donor.getAvailabilitySlots() != null) {
            donor.getAvailabilitySlots().forEach(slot -> slot.setDonor(donor));
        }
        if (donor.getDonationHistory() != null) {
            donor.getDonationHistory().forEach(h -> h.setDonor(donor));
        }
        try {
            Donor saved = donorRepository.save(donor);
            log.info("Donor registered: {} ({})", saved.getName(), saved.getId());
            // Auto-provision login: username = name, password = phone number
            try {
                authService.createDonorAccount(saved);
            } catch (Exception e) {
                log.warn("Donor login account not created for {}: {}", saved.getId(), e.getMessage());
            }
            return saved;
        } catch (Exception e) {
            log.error("Donor registration failed: {}", e.getMessage());
            throw new Exception("Registration could not be completed. Please check your details and try again.");
        }
    }

    /** Login username auto-generated at registration (for success messages). */
    @Transactional(readOnly = true)
    public String getDonorLoginUsername(String donorId) {
        return userAccountRepository.findByDonorId(donorId)
                .map(UserAccount::getUsername).orElse(null);
    }

    public Donor updateDonor(Donor donor) throws Exception {
        Donor existing = donorRepository.findById(donor.getId())
                .orElseThrow(() -> new Exception("Donor not found"));

        if (donor.getName() == null || donor.getName().trim().length() < 2) {
            throw new Exception("Please enter your full name (minimum 2 characters).");
        }
        existing.setName(donor.getName().trim());
        if (donor.getPhone() != null && !donor.getPhone().isBlank()) {
            String cleanPhone = donor.getPhone().replaceAll("\\D", "");
            if (cleanPhone.length() < 9 || cleanPhone.length() > 15) {
                throw new Exception("Please enter a valid phone number (9 to 15 digits).");
            }
            if (!cleanPhone.equals(existing.getPhone()) && donorRepository.existsByPhone(cleanPhone)) {
                throw new Exception("This phone number belongs to another donor.");
            }
            existing.setPhone(cleanPhone);
        }
        existing.setCity(donor.getCity());
        if (donor.getProvince() != null) existing.setProvince(donor.getProvince());
        existing.setBloodGroup(donor.getBloodGroup());
        existing.setNotes(donor.getNotes());
        existing.setAvailability(donor.getAvailability());
        existing.setAllowCall(donor.isAllowCall());

        // Update availability slots
        existing.getAvailabilitySlots().clear();
        if (donor.getAvailabilitySlots() != null) {
            donor.getAvailabilitySlots().forEach(slot -> {
                slot.setDonor(existing);
                existing.getAvailabilitySlots().add(slot);
            });
        }
        return donorRepository.save(existing);
    }

    public Donor addDonation(String donorId, LocalDateTime date, String location, String notes) throws Exception {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new Exception("Donor not found"));

        LocalDateTime donationDate = date != null ? date : LocalDateTime.now();
        if (donationDate.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new Exception("Donation date cannot be in the future");
        }
        // Real-world NBTS rule: minimum 120-day gap between donations
        if (donor.getLastDonatedAt() != null) {
            long daysSince = ChronoUnit.DAYS.between(donor.getLastDonatedAt(), donationDate);
            if (daysSince < MIN_DONATION_GAP_DAYS && donationDate.isAfter(donor.getLastDonatedAt())) {
                long wait = MIN_DONATION_GAP_DAYS - daysSince;
                throw new Exception("Donor must wait " + wait + " more day(s) — minimum 4 months between donations");
            }
        }
        if (location == null || location.trim().isEmpty()) {
            throw new Exception("Donation location is required");
        }
        DonationHistory rec = new DonationHistory(donationDate, location.trim(), notes);
        rec.setDonor(donor);
        donor.getDonationHistory().add(rec);
        donor.setLastDonatedAt(donationDate);
        log.info("Donation recorded for donor {}", donorId);
        return donorRepository.save(donor);
    }

    /** Real-world eligibility check used by UI + QA before accepting a donation. */
    @Transactional(readOnly = true)
    public String checkDonorEligibility(String donorId) {
        return getDonorById(donorId).map(d -> {
            if (d.getLastDonatedAt() == null) return "ELIGIBLE";
            long days = ChronoUnit.DAYS.between(d.getLastDonatedAt(), LocalDateTime.now());
            if (days < MIN_DONATION_GAP_DAYS) return "WAIT:" + (MIN_DONATION_GAP_DAYS - days);
            return "ELIGIBLE";
        }).orElse("NOT_FOUND");
    }

    /** Units expiring within N days — drives real-world FEFO dispatch priority. */
    @Transactional(readOnly = true)
    public List<BloodUnit> getExpiringSoonUnits(int withinDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime limit = now.plusDays(withinDays);
        return bloodUnitRepository.findByStatusAndExpiresAtAfter("Approved - Available", now).stream()
                .filter(u -> u.getExpiresAt().isBefore(limit))
                .sorted(Comparator.comparing(BloodUnit::getExpiresAt))
                .collect(Collectors.toList());
    }

    /** Scheduler calls this nightly: auto-discard expired available AND reserved units. Returns count. */
    public int autoDiscardExpiredUnits() {
        LocalDateTime now = LocalDateTime.now();
        List<BloodUnit> expired = bloodUnitRepository.findByStatusAndExpiresAtBefore("Approved - Available", now);
        expired.addAll(bloodUnitRepository.findByStatusAndExpiresAtBefore("Reserved", now));
        for (BloodUnit u : expired) {
            u.setStatus("Discarded");
            u.setReservedForRequestId(null);
            String prefix = (u.getQualityNotes() != null && !u.getQualityNotes().isBlank()) ? u.getQualityNotes() + " | " : "";
            u.setQualityNotes(prefix + "DISCARDED: expired on " + u.getExpiresAt());
            bloodUnitRepository.save(u);
        }
        if (!expired.isEmpty()) log.warn("Auto-discarded {} expired blood units", expired.size());
        return expired.size();
    }

    /** Lightweight dashboard counts — avoids loading full tables on home page. */
    @Transactional(readOnly = true)
    public long countDonors() { return donorRepository.count(); }

    @Transactional(readOnly = true)
    public long countRequestsByStatus(String status) { return requestRepository.countByStatus(status); }

    /** Available unit count per blood group (fixed donor order) for the public stock board. */
    @Transactional(readOnly = true)
    public java.util.LinkedHashMap<String, Long> getStockByGroup() {
        java.util.LinkedHashMap<String, Long> map = new java.util.LinkedHashMap<>();
        for (String g : List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")) {
            map.put(g, countAvailableUnits(g, null, null));
        }
        return map;
    }

    // ─── Appointment booking (register → book slot → donate → history) ───────

    public Appointment bookAppointment(String donorId, String branch, LocalDateTime scheduledAt, String notes) throws Exception {
        Donor donor = donorRepository.findById(donorId).orElseThrow(() -> new Exception("Donor not found"));
        if (!"Available".equalsIgnoreCase(donor.getAvailability())) {
            throw new Exception("Your profile is marked Unavailable. Please update availability first.");
        }
        String elig = checkDonorEligibility(donorId);
        if (elig.startsWith("WAIT:")) {
            throw new Exception("You become eligible in " + elig.substring(5) + " day(s). Please pick a later date.");
        }
        if (scheduledAt == null || scheduledAt.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new Exception("Please choose a slot at least 2 hours in the future.");
        }
        boolean clash = appointmentRepository.findByDonorIdOrderByScheduledAtDesc(donorId).stream()
                .anyMatch(a -> "Scheduled".equals(a.getStatus()));
        if (clash) throw new Exception("You already have an upcoming appointment. Cancel it first to rebook.");
        Appointment a = new Appointment();
        a.setDonorId(donor.getId());
        a.setDonorName(donor.getName());
        a.setPhone(donor.getPhone());
        a.setBloodGroup(donor.getBloodGroup());
        a.setBranch(branch != null && !branch.isBlank() ? branch : "Main Blood Bank");
        a.setScheduledAt(scheduledAt);
        a.setNotes(notes);
        a.setStatus("Scheduled");
        log.info("Appointment booked: donor {} @ {} {}", donorId, a.getBranch(), scheduledAt);
        return appointmentRepository.save(a);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getDonorAppointments(String donorId) {
        return appointmentRepository.findByDonorIdOrderByScheduledAtDesc(donorId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getUpcomingAppointments() {
        return appointmentRepository.findByStatusOrderByScheduledAtAsc("Scheduled");
    }

    public Appointment cancelAppointment(String id, String donorId) throws Exception {
        Appointment a = appointmentRepository.findById(id).orElseThrow(() -> new Exception("Appointment not found"));
        if (donorId != null && !donorId.equals(a.getDonorId())) throw new Exception("Not your appointment.");
        if (!"Scheduled".equals(a.getStatus())) throw new Exception("Only scheduled appointments can be cancelled.");
        a.setStatus("Cancelled");
        return appointmentRepository.save(a);
    }

    public Appointment completeAppointment(String id) throws Exception {
        Appointment a = appointmentRepository.findById(id).orElseThrow(() -> new Exception("Appointment not found"));
        if (!"Scheduled".equals(a.getStatus())) throw new Exception("Only scheduled appointments can be completed.");
        a.setStatus("Completed");
        return appointmentRepository.save(a);
    }

    /** Real-world: compatible available units for a recipient group (FEFO order not applied here). */
    @Transactional(readOnly = true)
    public List<BloodUnit> findCompatibleAvailable(String recipientGroup, String component, String branch) {
        List<String> groups = compatibleGroups(recipientGroup);
        LocalDateTime now = LocalDateTime.now();
        return bloodUnitRepository.findByStatusAndExpiresAtAfter("Approved - Available", now).stream()
                .filter(u -> groups.stream().anyMatch(g -> g.equalsIgnoreCase(u.getBloodGroup())))
                .filter(u -> component == null || component.isBlank() || u.getComponent().equalsIgnoreCase(component))
                .filter(u -> branch == null || branch.isBlank() || u.getBranch().equalsIgnoreCase(branch))
                .sorted(Comparator.comparing(BloodUnit::getExpiresAt))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countCompatibleAvailable(String recipientGroup, String component, String branch) {
        return findCompatibleAvailable(recipientGroup, component, branch).size();
    }

    /**
     * Record a real collection: unit taken FROM an eligible donor.
     * Enforces the 120-day gap, creates a Pending Screening unit linked to the
     * donor, and updates donation history + lastDonatedAt in one transaction.
     */
    public BloodUnit collectDonation(String donorId, String component, String branch, String location) throws Exception {
        Donor donor = donorRepository.findById(donorId).orElseThrow(() -> new Exception("Donor not found"));
        if (!"Available".equalsIgnoreCase(donor.getAvailability())) {
            throw new Exception(donor.getName() + " is marked Unavailable and cannot donate right now.");
        }
        String elig = checkDonorEligibility(donorId);
        if (elig.startsWith("WAIT:")) {
            throw new Exception(donor.getName() + " must wait " + elig.substring(5) + " more day(s) before donating again.");
        }
        if (component == null || component.isBlank()) component = "Whole Blood";
        if (branch == null || branch.isBlank()) branch = "Main Blood Bank";
        BloodUnit unit = new BloodUnit();
        unit.setBloodGroup(donor.getBloodGroup());
        unit.setComponent(component);
        unit.setBranch(branch);
        unit.setDonorId(donor.getId());
        unit.setExpiresAt(LocalDateTime.now().plusDays("Platelets".equalsIgnoreCase(component) ? 5 : "Plasma".equalsIgnoreCase(component) ? 90 : 35));
        unit.setStatus("Pending Screening");
        unit.setCollectedAt(LocalDateTime.now());
        bloodUnitRepository.save(unit);
        addDonation(donor.getId(), LocalDateTime.now(), location != null && !location.isBlank() ? location.trim() : branch,
                "Collection recorded: unit " + unit.getId());
        log.info("Collection recorded: donor {} → unit {}", donor.getId(), unit.getId());
        return unit;
    }

    public boolean deleteDonor(String id) {
        if (donorRepository.existsById(id)) {
            // Remove linked self-service login account too
            userAccountRepository.findByDonorId(id).ifPresent(userAccountRepository::delete);
            donorRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // ─── Request operations ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Request> getRequests(String status, String bloodGroup, String city) {
        List<Request> all = requestRepository.findAll();
        return all.stream()
            .filter(r -> {
                if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
                    if (!r.getStatus().equalsIgnoreCase(status)) return false;
                }
                if (bloodGroup != null && !bloodGroup.isEmpty() && !bloodGroup.equalsIgnoreCase("Any")) {
                    if (!r.getBloodGroup().equalsIgnoreCase(bloodGroup)) return false;
                }
                if (city != null && !city.isEmpty()) {
                    if (r.getCity() == null || !r.getCity().toLowerCase().contains(city.toLowerCase().trim()))
                        return false;
                }
                return true;
            })
            .sorted(Comparator.comparing(Request::getNeededBy, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Request::getCreatedAt, Comparator.reverseOrder()))
            .limit(200)
            .collect(Collectors.toList());
    }

    public Request createHospitalRequisition(Request req) throws Exception {
        req.setRequestType("Hospital Requisition");
        validateRequest(req);
        long available = countCompatibleAvailable(req.getBloodGroup(), req.getComponent(), null);
        req.setStatus(available >= req.getUnits() ? "Submitted" : "Partial Match");
        return requestRepository.save(req);
    }

    public Request createRequest(Request req) throws Exception {
        validateRequest(req);
        if (req.getRequestType() == null || req.getRequestType().isBlank()) {
            req.setRequestType("Patient Request");
        }
        long available = countCompatibleAvailable(req.getBloodGroup(), req.getComponent(), null);
        req.setStatus(available >= req.getUnits() ? "Pending" : "Partial Match");
        return requestRepository.save(req);
    }

    private void validateRequest(Request req) throws Exception {
        if (req.getPatientName() == null || req.getPatientName().trim().length() < 2) {
            throw new Exception("Invalid patient name (min 2 characters)");
        }
        if (req.getBloodGroup() == null || req.getBloodGroup().isEmpty()) {
            throw new Exception("Please specify blood group needed");
        }
        if (req.getComponent() == null || req.getComponent().isBlank()) {
            throw new Exception("Please specify the blood component needed");
        }
        if (req.getCity() == null || req.getCity().trim().isEmpty()) {
            throw new Exception("Please specify city");
        }
        if (req.getUnits() < 1 || req.getUnits() > 10) {
            throw new Exception("Units must be between 1 and 10 per request");
        }
        String cleanPhone = req.getContactPhone() != null ? req.getContactPhone().replaceAll("\\D", "") : "";
        if (cleanPhone.length() < 9 || cleanPhone.length() > 15) {
            throw new Exception("Invalid contact phone number (must be 9 to 15 digits)");
        }
        req.setContactPhone(cleanPhone);
        if (req.getNeededBy() == null) {
            req.setNeededBy(LocalDateTime.now().plusDays(2));
        } else if (req.getNeededBy().isBefore(LocalDateTime.now().minusHours(1))) {
            throw new Exception("Needed-by date cannot be in the past");
        }
    }

    public Request updateRequestStatus(String id, String status) throws Exception {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new Exception("Request not found"));
        List<String> validStatuses = List.of("Pending", "Partial Match", "Submitted", "Approved", "Rejected", "Dispatch Scheduled", "Delivered", "Cancelled");
        if (!validStatuses.contains(status)) throw new Exception("Invalid request status");
        request.setStatus(status);
        return requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<DeliveryOrder> getDeliveryOrders() {
        return deliveryOrderRepository.findAll().stream().sorted(Comparator.comparing(DeliveryOrder::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))).collect(Collectors.toList());
    }

    public DeliveryOrder scheduleDelivery(String requestId, String courierName, String vehicleNumber, LocalDateTime scheduledAt, String notes) throws Exception {
        Request request = requestRepository.findById(requestId).orElseThrow(() -> new Exception("Request not found"));
        if (!"Approved".equals(request.getStatus())) throw new Exception("Only approved requests can be scheduled for delivery");
        if (courierName == null || courierName.isBlank() || vehicleNumber == null || vehicleNumber.isBlank() || scheduledAt == null) throw new Exception("Courier, vehicle and schedule are required");
        // Real flow: dispatch requires locked units (auto-reserves legacy approvals)
        reserveUnits(request);
        DeliveryOrder order = new DeliveryOrder(); order.setRequest(request); order.setCourierName(courierName); order.setVehicleNumber(vehicleNumber); order.setScheduledAt(scheduledAt); order.setNotes(notes);
        request.setStatus("Dispatch Scheduled"); requestRepository.save(request); return deliveryOrderRepository.save(order);
    }

    public void updateDeliveryStatus(String id, String status) throws Exception {
        DeliveryOrder order = deliveryOrderRepository.findById(id).orElseThrow(() -> new Exception("Delivery order not found"));
        if (!List.of("Scheduled", "Delivered", "Cancelled").contains(status)) throw new Exception("Invalid delivery status");
        order.setStatus(status);
        String reqId = order.getRequest() != null ? order.getRequest().getId() : null;
        if ("Delivered".equals(status)) {
            order.setDeliveredAt(LocalDateTime.now());
            order.getRequest().setStatus("Delivered");
            if (reqId != null) consumeReservations(reqId);
        }
        if ("Cancelled".equals(status)) {
            // Units stay Reserved for this request so it can be re-scheduled immediately
            order.getRequest().setStatus("Approved");
        }
        deliveryOrderRepository.save(order);
    }

    public DeliveryVehicle registerVehicle(DeliveryVehicle vehicle) throws Exception {
        if (vehicle.getVehicleNumber() == null || vehicle.getVehicleNumber().isBlank()) throw new Exception("Vehicle number is required");
        if (vehicleRepository.existsByVehicleNumber(vehicle.getVehicleNumber())) throw new Exception("Vehicle already registered");
        vehicle.setStatus("Available");
        return vehicleRepository.save(vehicle);
    }

    public DeliveryCourier registerCourier(DeliveryCourier courier) throws Exception {
        if (courier.getName() == null || courier.getName().isBlank()) throw new Exception("Courier name is required");
        if (courier.getLicenseNumber() == null || courier.getLicenseNumber().isBlank()) throw new Exception("Driver license is required");
        if (courier.getContact() == null || courier.getContact().isBlank()) throw new Exception("Contact number is required");
        courier.setStatus("Available");
        return courierRepository.save(courier);
    }

    @Transactional(readOnly = true)
    public List<DeliveryVehicle> getAvailableVehicles() {
        return vehicleRepository.findByStatus("Available");
    }

    @Transactional(readOnly = true)
    public List<DeliveryCourier> getAvailableCouriers() {
        return courierRepository.findByStatus("Available");
    }

    @Transactional(readOnly = true)
    public List<DeliveryVehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DeliveryCourier> getAllCouriers() {
        return courierRepository.findAll();
    }

    public Request approveRequest(String id) throws Exception {
        Request req = requestRepository.findById(id).orElseThrow(() -> new Exception("Request not found"));
        if (!List.of("Pending", "Partial Match", "Submitted").contains(req.getStatus())) {
            throw new Exception("Only Pending/Submitted requests can be approved (current: " + req.getStatus() + ").");
        }
        // Real flow: approving LOCKS compatible units (FEFO) so stock can't be double-promised
        reserveUnits(req);
        req.setStatus("Approved");
        log.info("Request {} approved with {} reserved units", id, req.getUnits());
        return requestRepository.save(req);
    }

    /** Lock the oldest compatible available units for a request (FEFO). */
    public int reserveUnits(Request req) throws Exception {
        List<BloodUnit> matched = findCompatibleAvailable(req.getBloodGroup(), req.getComponent(), null);
        long already = bloodUnitRepository.findAll().stream()
                .filter(u -> "Reserved".equals(u.getStatus()) && req.getId().equals(u.getReservedForRequestId()))
                .count();
        int need = req.getUnits() - (int) already;
        if (need <= 0) return (int) already;
        if (matched.size() < need) {
            throw new Exception("Cannot reserve: only " + matched.size() + " compatible unit(s) available for "
                    + req.getBloodGroup() + " " + req.getComponent() + ", need " + need + " more.");
        }
        for (int i = 0; i < need; i++) {
            BloodUnit u = matched.get(i);
            u.setStatus("Reserved");
            u.setReservedForRequestId(req.getId());
            bloodUnitRepository.save(u);
        }
        return req.getUnits();
    }

    /** Release units reserved for a request back to Available. */
    public int releaseReservations(String requestId) {        List<BloodUnit> reserved = bloodUnitRepository.findAll().stream()
                .filter(u -> "Reserved".equals(u.getStatus()) && requestId.equals(u.getReservedForRequestId()))
                .collect(Collectors.toList());
        for (BloodUnit u : reserved) {
            u.setStatus("Approved - Available");
            u.setReservedForRequestId(null);
            bloodUnitRepository.save(u);
        }
        return reserved.size();
    }

    /** Consume reserved units on successful transfusion/delivery. */
    public int consumeReservations(String requestId) {
        List<BloodUnit> reserved = bloodUnitRepository.findAll().stream()
                .filter(u -> "Reserved".equals(u.getStatus()) && requestId.equals(u.getReservedForRequestId()))
                .collect(Collectors.toList());
        for (BloodUnit u : reserved) {
            u.setStatus("Delivered");
            bloodUnitRepository.save(u);
        }
        return reserved.size();
    }

    // ─── Contact message operations ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContactMessage> getAllMessages() {
        return messageRepository.findAll().stream()
            .sorted(Comparator.comparing(ContactMessage::getCreatedAt, Comparator.reverseOrder()))
            .collect(Collectors.toList());
    }

    public ContactMessage createMessage(String name, String email, String subject, String message) throws Exception {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new Exception("Name is required (max 100 chars)");
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) throw new Exception("Valid email is required");
        if (subject == null || subject.trim().isEmpty() || subject.trim().length() > 200) throw new Exception("Subject is required (max 200 chars)");
        if (message == null || message.trim().isEmpty() || message.trim().length() > 2000) throw new Exception("Message content is required (max 2000 chars)");

        ContactMessage msg = new ContactMessage(name.trim(), email.trim().toLowerCase(), subject.trim(), message.trim());
        return messageRepository.save(msg);
    }

    public ContactMessage markMessageAsRead(String id) throws Exception {
        ContactMessage msg = messageRepository.findById(id)
                .orElseThrow(() -> new Exception("Message not found with id: " + id));
        msg.setStatus("read");
        return messageRepository.save(msg);
    }

    public boolean deleteMessage(String id) {
        if (messageRepository.existsById(id)) {
            messageRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // ─── Extended CRUD operations for Stakeholder Roles ────────────────────────

    public Request updateRequestDetails(String id, Integer units, LocalDateTime neededBy, String phone, String notes, String urgency) throws Exception {
        Request req = requestRepository.findById(id).orElseThrow(() -> new Exception("Request not found"));
        if (units != null && units > 0) req.setUnits(units);
        if (neededBy != null) req.setNeededBy(neededBy);
        if (phone != null && !phone.isBlank()) req.setContactPhone(phone.replaceAll("\\D", ""));
        if (notes != null) req.setNotes(notes);
        if (urgency != null && !urgency.isBlank()) req.setUrgency(urgency);
        return requestRepository.save(req);
    }

    public boolean deleteRequest(String id) {
        if (requestRepository.existsById(id)) {
            releaseReservations(id);
            deliveryOrderRepository.findAll().stream()
                    .filter(o -> o.getRequest() != null && o.getRequest().getId().equals(id))
                    .findFirst()
                    .ifPresent(deliveryOrderRepository::delete);
            requestRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public BloodUnit discardBloodUnit(String id, String reason) throws Exception {
        BloodUnit unit = bloodUnitRepository.findById(id).orElseThrow(() -> new Exception("Blood unit not found"));
        unit.setStatus("Discarded");
        String prefix = (unit.getQualityNotes() != null && !unit.getQualityNotes().isBlank()) ? unit.getQualityNotes() + " | " : "";
        unit.setQualityNotes(prefix + "DISCARDED: " + (reason != null && !reason.isBlank() ? reason : "Safety non-compliance"));
        return bloodUnitRepository.save(unit);
    }

    public BloodUnit updateBloodUnitNotes(String id, String notes) throws Exception {
        BloodUnit unit = bloodUnitRepository.findById(id).orElseThrow(() -> new Exception("Blood unit not found"));
        unit.setQualityNotes(notes);
        return bloodUnitRepository.save(unit);
    }

    public DeliveryVehicle updateVehicleStatus(String id, String status) throws Exception {
        DeliveryVehicle vehicle = vehicleRepository.findById(id).orElseThrow(() -> new Exception("Vehicle not found"));
        vehicle.setStatus(status);
        return vehicleRepository.save(vehicle);
    }

    public boolean deleteVehicle(String id) {
        if (vehicleRepository.existsById(id)) {
            vehicleRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public DeliveryCourier updateCourierStatus(String id, String status) throws Exception {
        DeliveryCourier courier = courierRepository.findById(id).orElseThrow(() -> new Exception("Courier not found"));
        courier.setStatus(status);
        return courierRepository.save(courier);
    }

    public boolean deleteCourier(String id) {
        if (courierRepository.existsById(id)) {
            courierRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public void cancelDeliveryOrder(String id) throws Exception {
        DeliveryOrder order = deliveryOrderRepository.findById(id).orElseThrow(() -> new Exception("Delivery order not found"));
        order.setStatus("Cancelled");
        if (order.getRequest() != null) {
            order.getRequest().setStatus("Approved");
            requestRepository.save(order.getRequest());
        }
        deliveryOrderRepository.save(order);
    }

    public void registerStockBatch(String bloodGroup, String component, String branch, int units, int expiryDays) throws Exception {
        if (bloodGroup == null || bloodGroup.isBlank()) throw new Exception("Blood group is required");
        if (units <= 0 || units > 100) throw new Exception("Units must be between 1 and 100 per batch");
        int days = expiryDays > 0 ? Math.min(expiryDays, 365) : 35;
        for (int i = 0; i < units; i++) {
            BloodUnit unit = new BloodUnit();
            unit.setBloodGroup(bloodGroup);
            unit.setComponent(component != null && !component.isBlank() ? component : "Whole Blood");
            unit.setBranch(branch != null && !branch.isBlank() ? branch : "Main Blood Bank");
            unit.setExpiresAt(LocalDateTime.now().plusDays(days));
            unit.setStatus("Pending Screening");
            bloodUnitRepository.save(unit);
        }
        log.info("Stock batch registered: {}x {} {} @ {}", units, bloodGroup, component, branch);
    }

    /**
     * Manager quantity correction on live stock.
     * delta > 0 → intake (Pending Screening, needs QA). 
     * delta < 0 → write-off oldest available units first (FEFO) as Discarded.
     * Returns the updated available count for that group/component/branch.
     */
    public long adjustStockQuantity(String bloodGroup, String component, String branch, int delta, String reason) throws Exception {
        if (bloodGroup == null || bloodGroup.isBlank()) throw new Exception("Blood group is required");
        final String comp = (component == null || component.isBlank()) ? "Whole Blood" : component;
        if (branch == null || branch.isBlank()) throw new Exception("Branch is required");
        if (delta == 0) throw new Exception("Enter a non-zero quantity change.");
        if (Math.abs(delta) > 100) throw new Exception("One adjustment is limited to 100 units.");
        if (delta > 0) {
            registerStockBatch(bloodGroup, comp, branch, delta, 35);
        } else {
            int need = -delta;
            LocalDateTime now = LocalDateTime.now();
            List<BloodUnit> available = bloodUnitRepository.findByStatusAndExpiresAtAfter("Approved - Available", now).stream()
                    .filter(u -> u.getBloodGroup().equalsIgnoreCase(bloodGroup))
                    .filter(u -> u.getComponent().equalsIgnoreCase(comp))
                    .filter(u -> u.getBranch().equalsIgnoreCase(branch))
                    .sorted(Comparator.comparing(BloodUnit::getExpiresAt))
                    .limit(need)
                    .collect(Collectors.toList());
            if (available.size() < need) {
                throw new Exception("Only " + available.size() + " available unit(s) to reduce — cannot remove " + need + ".");
            }
            String note = "Manager adjustment" + (reason != null && !reason.isBlank() ? ": " + reason.trim() : ": manual quantity correction");
            for (BloodUnit u : available) {
                u.setStatus("Discarded");
                String prefix = (u.getQualityNotes() != null && !u.getQualityNotes().isBlank()) ? u.getQualityNotes() + " | " : "";
                u.setQualityNotes(prefix + note);
                bloodUnitRepository.save(u);
            }
            log.warn("Stock reduced by manager: -{} {} {} @ {} ({})", need, bloodGroup, comp, branch, note);
        }
        return countAvailableUnits(bloodGroup, comp, branch);
    }

    public Request rejectRequest(String id, String reason) throws Exception {
        Request req = requestRepository.findById(id).orElseThrow(() -> new Exception("Request not found"));
        releaseReservations(id);
        req.setStatus("Rejected");
        if (reason != null && !reason.isBlank()) {
            String existingNotes = req.getNotes() != null ? req.getNotes() + " | " : "";
            req.setNotes(existingNotes + "Rejection: " + reason);
        }
        return requestRepository.save(req);
    }
}
