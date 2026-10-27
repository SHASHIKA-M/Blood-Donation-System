package com.blooddonation.spring.service;

import com.blooddonation.spring.model.PermissionConfig;
import com.blooddonation.spring.model.UserAccount;
import com.blooddonation.spring.model.UserRole;
import com.blooddonation.spring.repository.AuditLogRepository;
import com.blooddonation.spring.repository.BloodUnitRepository;
import com.blooddonation.spring.repository.PermissionConfigRepository;
import com.blooddonation.spring.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT tests (Mockito, no DB): IT Operations Supervisor rules in AuthService.
 * Covers password policy, RBAC checks, user provisioning guards, login guards.
 * Run: mvn test -Dtest=AuthServiceTest
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserAccountRepository userRepo;
    @Mock PermissionConfigRepository permRepo;
    @Mock AuditLogRepository auditRepo;
    @Mock BloodUnitRepository bloodUnitRepo;
    @Mock javax.sql.DataSource dataSource;

    @InjectMocks AuthService authService;

    private UserAccount user(String username, String rawPassword, UserRole role, boolean active) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(new BCryptPasswordEncoder(12).encode(rawPassword));
        u.setFullName("Test User");
        u.setRole(role);
        u.setActive(active);
        return u;
    }

    // ── password policy ───────────────────────────────────────────────────

    @Test
    void validatePassword_rejectsShortPassword() {
        Exception ex = assertThrows(Exception.class, () -> authService.validatePassword("1234567"));
        assertTrue(ex.getMessage().contains("8 characters"));
    }

    @Test
    void validatePassword_acceptsMinimumLength() {
        assertDoesNotThrow(() -> authService.validatePassword("pass1234"));
    }

    // ── RBAC ──────────────────────────────────────────────────────────────

    @Test
    void hasPermission_itAdminAlwaysTrue() {
        assertTrue(authService.hasPermission(UserRole.IT_ADMIN, "ANYTHING_AT_ALL"));
    }

    @Test
    void hasPermission_nullGuardsFailClosed() {
        assertFalse(authService.hasPermission(null, "VIEW_STOCK"));
        assertFalse(authService.hasPermission(UserRole.DONOR, null));
    }

    @Test
    void hasPermission_readsDbRow() {
        when(permRepo.findById("DONOR")).thenReturn(Optional.of(new PermissionConfig("DONOR", "A,B")));
        assertTrue(authService.hasPermission(UserRole.DONOR, "A"));
        assertFalse(authService.hasPermission(UserRole.DONOR, "Z"));
    }

    @Test
    void getPortalPath_coversAllSevenRoles() {
        assertEquals("/portal/recipient", authService.getPortalPath(UserRole.RECIPIENT));
        assertEquals("/portal/donor", authService.getPortalPath(UserRole.DONOR));
        assertEquals("/portal/hospital", authService.getPortalPath(UserRole.HOSPITAL_COORDINATOR));
        assertEquals("/portal/qa", authService.getPortalPath(UserRole.QA_OFFICER));
        assertEquals("/portal/delivery", authService.getPortalPath(UserRole.DELIVERY_OFFICER));
        assertEquals("/portal/manager", authService.getPortalPath(UserRole.BLOOD_BANK_MANAGER));
        assertEquals("/portal/it", authService.getPortalPath(UserRole.IT_ADMIN));
    }

    @Test
    void getHomePath_donorLoginGoesToDonorPortalOnly() {
        UserAccount donorLogin = user("aman silva", "0771234567", UserRole.DONOR, true);
        donorLogin.setDonorId("d1");
        assertEquals("/portal/donor", authService.getHomePath(donorLogin));

        UserAccount staff = user("qa", "pass12345", UserRole.QA_OFFICER, true);
        assertEquals("/portal/qa", authService.getHomePath(staff));
    }

    // ── user provisioning ─────────────────────────────────────────────────

    @Test
    void createUser_rejectsShortUsername() {
        Exception ex = assertThrows(Exception.class,
                () -> authService.createUser("ab", "pass12345", "Full Name", UserRole.RECIPIENT, null));
        assertTrue(ex.getMessage().contains("3 characters"));
    }

    @Test
    void createUser_rejectsDuplicateUsername() {
        when(userRepo.existsByUsername("qauser")).thenReturn(true);
        Exception ex = assertThrows(Exception.class,
                () -> authService.createUser("qauser", "pass12345", "Full Name", UserRole.QA_OFFICER, null));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void createUser_rejectsShortPasswordBeforeHashing() {
        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        Exception ex = assertThrows(Exception.class,
                () -> authService.createUser("newuser", "short", "Full Name", UserRole.RECIPIENT, null));
        assertTrue(ex.getMessage().contains("8 characters"));
        verify(userRepo, never()).save(any());
    }

    @Test
    void createUser_lowercasesUsernameAndHashesPassword() throws Exception {
        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(userRepo.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAccount saved = authService.createUser("NewUser", "pass12345", "New User", UserRole.RECIPIENT, null);

        assertEquals("newuser", saved.getUsername());
        assertNotEquals("pass12345", saved.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder(12).matches("pass12345", saved.getPasswordHash()));
    }

    @Test
    void deleteUser_blocksSelfDelete() {
        UserAccount me = user("itadmin", "pass12345", UserRole.IT_ADMIN, true);
        when(userRepo.findById("id1")).thenReturn(Optional.of(me));
        Exception ex = assertThrows(Exception.class, () -> authService.deleteUser("id1", "ITADMIN"));
        assertTrue(ex.getMessage().toLowerCase().contains("currently logged-in"));
        verify(userRepo, never()).delete(any());
    }

    @Test
    void deleteUser_blocksRootAdmin() {
        UserAccount root = user("admin", "pass12345", UserRole.IT_ADMIN, true);
        when(userRepo.findById("id9")).thenReturn(Optional.of(root));
        Exception ex = assertThrows(Exception.class, () -> authService.deleteUser("id9", "itadmin"));
        assertTrue(ex.getMessage().toLowerCase().contains("root admin"));
        verify(userRepo, never()).delete(any());
    }

    @Test
    void deleteUser_removesOtherAccount() throws Exception {
        UserAccount other = user("qa", "pass12345", UserRole.QA_OFFICER, true);
        when(userRepo.findById("id2")).thenReturn(Optional.of(other));
        authService.deleteUser("id2", "itadmin");
        verify(userRepo).delete(other);
    }

    // ── login guards ──────────────────────────────────────────────────────

    @Test
    void authenticate_nullGuardsReturnEmpty() {
        assertTrue(authService.authenticate(null, "x").isEmpty());
        assertTrue(authService.authenticate("u", null).isEmpty());
    }

    @Test
    void authenticate_blocksInactiveAccount() {
        UserAccount disabled = user("qa", "pass12345", UserRole.QA_OFFICER, false);
        when(userRepo.findByUsername("qa")).thenReturn(Optional.of(disabled));
        assertTrue(authService.authenticate("qa", "pass12345").isEmpty());
    }

    @Test
    void authenticate_succeedsWithCorrectPassword() {
        UserAccount qa = user("qa", "pass12345", UserRole.QA_OFFICER, true);
        when(userRepo.findByUsername("qa")).thenReturn(Optional.of(qa));
        assertTrue(authService.authenticate("qa", "pass12345").isPresent());
    }

    @Test
    void authenticate_failsWithWrongPassword() {
        UserAccount qa = user("qa", "pass12345", UserRole.QA_OFFICER, true);
        // same stub serves both the normalised and the legacy fallback lookup
        when(userRepo.findByUsername("qa")).thenReturn(Optional.of(qa));
        assertTrue(authService.authenticate("qa", "wrongpass1").isEmpty());
    }
}
