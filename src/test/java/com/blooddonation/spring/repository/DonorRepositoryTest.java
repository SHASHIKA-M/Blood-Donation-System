package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.Donor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INTEGRATION slice test (@DataJpaTest + H2 in-memory DB, no SQL Server needed).
 * Proves the Donor CRUD queries the service layer relies on.
 * Run: mvn test -Dtest=DonorRepositoryTest
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DonorRepositoryTest {

    @Autowired DonorRepository donorRepository;

    private Donor donor(String name, String phone, String group, String city) {
        Donor d = new Donor();
        d.setName(name);
        d.setPhone(phone);
        d.setBloodGroup(group);
        d.setCity(city);
        d.setAvailability("Available");
        return d;
    }

    @Test
    void saveAndFindByPhone() {
        donorRepository.save(donor("Aman Silva", "0771234567", "B+", "Colombo"));

        Optional<Donor> found = donorRepository.findByPhone("0771234567");

        assertTrue(found.isPresent());
        assertEquals("Aman Silva", found.get().getName());
        assertTrue(donorRepository.existsByPhone("0771234567"));
        assertFalse(donorRepository.existsByPhone("0000000000"));
    }

    @Test
    void findByBloodGroupIgnoreCase() {
        donorRepository.save(donor("Aman Silva", "0771234567", "B+", "Colombo"));
        donorRepository.save(donor("Priya Perera", "0712345678", "O-", "Kandy"));

        List<Donor> bPos = donorRepository.findByBloodGroupIgnoreCase("b+");

        assertEquals(1, bPos.size());
        assertEquals("Aman Silva", bPos.get(0).getName());
    }

    @Test
    void deleteRemovesDonor() {
        Donor saved = donorRepository.save(donor("Aman Silva", "0771234567", "B+", "Colombo"));
        donorRepository.deleteById(saved.getId());
        assertTrue(donorRepository.findById(saved.getId()).isEmpty());
    }
}
