package com.blooddonation.spring.service;

import com.blooddonation.spring.model.*;
import com.blooddonation.spring.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String SESSION_USER = "currentUser";
    public static final String SESSION_ROLE = "currentRole";

    public static final List<String> ADMIN_CONSOLE_SCOPES = List.of(
            "ACCESS_ADMIN_CONSOLE",
            "ADMIN_DASHBOARD",
            "ADMIN_DELIVERIES",
            "ADMIN_DONORS",
            "ADMIN_INVENTORY",
            "ADMIN_MESSAGES",
            "ADMIN_REQUESTS"
    );

    private static final Map<UserRole, String> DEFAULT_PERMISSIONS = Map.of(
            UserRole.RECIPIENT, "VIEW_AVAILABILITY,SUBMIT_REQUEST,TRACK_REQUEST",
            UserRole.DONOR, "VIEW_AVAILABILITY,BOOK_APPOINTMENT,UPDATE_PROFILE,TRACK_REQUEST",
            UserRole.HOSPITAL_COORDINATOR, "VIEW_AVAILABILITY,SUBMIT_REQUISITION,TRACK_DISPATCH,MANAGE_TRANSFER",
            UserRole.QA_OFFICER, "VIEW_INVENTORY,LOG_SCREENING,VERIFY_EXPIRY,LOG_DISCARD",
            UserRole.DELIVERY_OFFICER, "REGISTER_VEHICLE,REGISTER_COURIER,SCHEDULE_DELIVERY,CANCEL_DELIVERY,CONFIRM_DELIVERY",
            UserRole.BLOOD_BANK_MANAGER, "VIEW_STOCK,APPROVE_REQUEST,EMERGENCY_DISPATCH,VIEW_REPORTS,LOW_STOCK_ALERT",
            UserRole.IT_ADMIN, "MANAGE_USERS,MANAGE_PERMISSIONS,VIEW_AUDIT,CONFIGURE_SYSTEM,ACCESS_ADMIN_CONSOLE,ADMIN_DASHBOARD,ADMIN_DELIVERIES,ADMIN_DONORS,ADMIN_INVENTORY,ADMIN_MESSAGES,ADMIN_REQUESTS"
    );

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Autowired private UserAccountRepository userRepo;
    @Autowired private PermissionConfigRepository permRepo;
    @Autowired private AuditLogRepository auditRepo;
    @Autowired private BloodUnitRepository bloodUnitRepo;

    @Autowired
    private javax.sql.DataSource dataSource;

    /** Drop stale CHECK constraints on user_accounts.role (Hibernate never alters them).
     *  Plain JDBC with auto-commit: JPA has no transaction in @PostConstruct. */
    private void dropRoleCheckConstraints() {
        try (java.sql.Connection c = dataSource.getConnection();
             java.sql.Statement s = c.createStatement();
             java.sql.ResultSet rs = s.executeQuery(
                     "SELECT cc.name FROM sys.check_constraints cc " +
                     "JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id " +
                     "JOIN sys.tables t ON t.object_id = cc.parent_object_id " +
                     "WHERE t.name = 'user_accounts' AND c.name = 'role'")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            while (rs.next()) names.add(rs.getString(1));
            for (String name : names) {
                if (name != null && name.matches("[A-Za-z0-9_]+")) {
                    try (java.sql.Statement d = c.createStatement()) {
                        d.execute("ALTER TABLE user_accounts DROP CONSTRAINT [" + name + "]");
                        log.info("Dropped stale role CHECK constraint: {}", name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not drop role CHECK constraints (non-SQLServer DB?): {}", e.getMessage());
        }
    }

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @PostConstruct
    public void seedUsersAndPermissions() {
        for (Map.Entry<UserRole, String> entry : DEFAULT_PERMISSIONS.entrySet()) {
            if (permRepo.findById(entry.getKey().name()).isEmpty()) {
                permRepo.save(new PermissionConfig(entry.getKey().name(), entry.getValue()));
            }
        }
        // One-time migration: donor-linked accounts created as RECIPIENT → real DONOR role.
        // The DB CHECK constraint on user_accounts.role only allows old enum values,
        // and ddl-auto=update never alters it — so drop stale constraints first.
        dropRoleCheckConstraints();
        for (UserAccount u : userRepo.findAll()) {
            if (u.getDonorId() != null && !u.getDonorId().isBlank() && u.getRole() == UserRole.RECIPIENT) {
                try {
                    u.setRole(UserRole.DONOR);
                    userRepo.save(u);
                    log.info("Migrated donor login to DONOR role: {}", u.getUsername());
                } catch (Exception e) {
                    log.error("Donor role migration failed for {}: {}", u.getUsername(), e.getMessage());
                }
            }
        }
        // Real-world: never seed weak demo passwords in production.
        // Set APP_SEED=false env var on real deploys and create users via IT portal.
        if (!seedEnabled) {
            log.info("Demo user seeding disabled (app.seed.enabled=false)");
            return;
        }
        if (userRepo.count() == 0) {
            seedUser("recipient", "Weerasuriya M.W.K.C.P.", UserRole.RECIPIENT, null);
            seedUser("hospital", "Piumal K.P.N.", UserRole.HOSPITAL_COORDINATOR, "National Hospital Colombo");
            seedUser("qa", "Rubasinghe S.S.", UserRole.QA_OFFICER, null);
            seedUser("delivery", "Jayasekara G.H.D.T.K.", UserRole.DELIVERY_OFFICER, null);
            seedUser("manager", "Widanapathirana S.K.N.", UserRole.BLOOD_BANK_MANAGER, null);
            seedUser("itadmin", "Wilabada W.K.S.M.", UserRole.IT_ADMIN, null);
        }
        // Root "admin" login removed — staff use named IT_ADMIN accounts (e.g. itadmin).
        // Delete the legacy row only when another active IT_ADMIN exists, so access is never lost.
        userRepo.findByUsername("admin").ifPresent(root -> {
            boolean hasReplacement = userRepo.findAll().stream()
                    .anyMatch(u -> !u.getId().equals(root.getId())
                            && u.getRole() == UserRole.IT_ADMIN && u.isActive());
            if (hasReplacement) {
                userRepo.delete(root);
                log.info("Removed legacy root 'admin' account (replaced by named IT_ADMIN accounts)");
            } else {
                log.warn("Keeping legacy root 'admin' account — no other active IT_ADMIN exists yet");
            }
        });
    }

    private void seedUser(String username, String fullName, UserRole role, String hospital) {
        seedUserWithPassword(username, fullName, role, hospital, "pass123");
    }

    private void seedUserWithPassword(String username, String fullName, UserRole role, String hospital, String password) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(fullName);
        u.setRole(role);
        u.setHospital(hospital);
        userRepo.save(u);
    }

    public Optional<UserAccount> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        String clean = username.trim().toLowerCase();
        Optional<UserAccount> result = userRepo.findByUsername(clean)
                .filter(UserAccount::isActive)
                .filter(u -> encoder.matches(password, u.getPasswordHash()));
        // Backwards-compat: older seeds stored mixed-case usernames
        if (result.isEmpty()) {
            result = userRepo.findByUsername(username.trim())
                    .filter(UserAccount::isActive)
                    .filter(u -> encoder.matches(password, u.getPasswordHash()));
        }
        if (result.isEmpty()) {
            log.warn("Failed login attempt for username '{}'", username);
        }
        return result;
    }

    public boolean hasPermission(UserRole role, String permission) {
        if (role == null || permission == null) return false;
        if (role == UserRole.IT_ADMIN) return true;
        return permRepo.findById(role.name())
                .map(p -> Arrays.asList(p.getPermissions().split(",")).contains(permission))
                .orElseGet(() -> {
                    String def = DEFAULT_PERMISSIONS.get(role);
                    return def != null && Arrays.asList(def.split(",")).contains(permission);
                });
    }

    public boolean hasAnyAdminPermission(UserRole role) {
        if (role == null) return false;
        if (role == UserRole.IT_ADMIN) return true;
        return ADMIN_CONSOLE_SCOPES.stream().anyMatch(scope -> hasPermission(role, scope));
    }

    public String getFirstAllowedAdminPath(UserRole role) {
        if (role == UserRole.IT_ADMIN || hasPermission(role, "ADMIN_DASHBOARD")) return "/admin/dashboard";
        if (hasPermission(role, "ADMIN_DONORS")) return "/admin/donors";
        if (hasPermission(role, "ADMIN_REQUESTS")) return "/admin/requests";
        if (hasPermission(role, "ADMIN_INVENTORY")) return "/admin/inventory";
        if (hasPermission(role, "ADMIN_DELIVERIES")) return "/admin/deliveries";
        if (hasPermission(role, "ADMIN_MESSAGES")) return "/admin/messages";
        return "/admin/dashboard";
    }

    public Map<UserRole, String> getAllPermissions() {
        Map<UserRole, String> map = new LinkedHashMap<>();
        for (UserRole role : UserRole.values()) {
            map.put(role, permRepo.findById(role.name())
                    .map(PermissionConfig::getPermissions)
                    .orElse(DEFAULT_PERMISSIONS.get(role)));
        }
        return map;
    }

    /** Distinct union of every known permission scope (defaults + DB), sorted — drives tick-box UI. */
    public List<String> getAllPermissionScopes() {
        java.util.Set<String> scopes = new java.util.TreeSet<>();
        DEFAULT_PERMISSIONS.values().forEach(csv -> scopes.addAll(Arrays.asList(csv.split(","))));
        scopes.addAll(ADMIN_CONSOLE_SCOPES);
        permRepo.findAll().forEach(p -> {
            if (p.getPermissions() != null) scopes.addAll(Arrays.asList(p.getPermissions().split(",")));
        });
        scopes.removeIf(s -> s == null || s.isBlank());
        return new java.util.ArrayList<>(scopes);
    }

    public List<String> getOperationalScopes() {
        List<String> scopes = getAllPermissionScopes();
        scopes.removeAll(ADMIN_CONSOLE_SCOPES);
        return scopes;
    }

    public void updatePermissions(UserRole role, String permissions, String actor) {
        PermissionConfig config = permRepo.findById(role.name())
                .orElse(new PermissionConfig(role.name(), permissions));
        config.setPermissions(permissions);
        permRepo.save(config);
        log(actor, "UPDATE_PERMISSIONS", "Updated permissions for " + role.name() + ": " + permissions);
    }

    public static final List<String> DEFAULT_BRANCHES = List.of("Colombo Central", "Kandy Branch", "Galle Branch");
    public static final List<String> ALL_BRANCHES = DEFAULT_BRANCHES;
    public static final String DEFAULT_ACTIVE_BRANCHES = "Colombo Central";

    public List<String> getAllBranches() {
        String csv = permRepo.findById("ALL_BRANCHES")
                .map(PermissionConfig::getPermissions)
                .orElse(String.join(",", DEFAULT_BRANCHES));
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    public void addBranch(String branchName, String actor) throws Exception {
        if (branchName == null || branchName.trim().length() < 2) {
            throw new Exception("Branch name must be at least 2 characters.");
        }
        String cleanName = branchName.trim();
        List<String> all = new ArrayList<>(getAllBranches());
        if (all.stream().anyMatch(b -> b.equalsIgnoreCase(cleanName))) {
            throw new Exception("Branch '" + cleanName + "' already exists.");
        }
        all.add(cleanName);
        String allCsv = String.join(",", all);
        PermissionConfig config = permRepo.findById("ALL_BRANCHES")
                .orElse(new PermissionConfig("ALL_BRANCHES", allCsv));
        config.setPermissions(allCsv);
        permRepo.save(config);

        // Also activate the new branch by default
        List<String> active = new ArrayList<>(getActiveBranches());
        if (!active.contains(cleanName)) {
            active.add(cleanName);
            updateBranchAccess(active, actor);
        }

        log(actor, "ADD_BRANCH", "Created new storage facility branch: " + cleanName);
    }

    public void deleteBranch(String branchName, String actor) throws Exception {
        if (branchName == null || branchName.isBlank()) {
            throw new Exception("Invalid branch name.");
        }
        String cleanName = branchName.trim();
        if ("Colombo Central".equalsIgnoreCase(cleanName)) {
            throw new Exception("Primary facility 'Colombo Central' cannot be deleted.");
        }
        List<String> all = new ArrayList<>(getAllBranches());
        boolean removed = all.removeIf(b -> b.equalsIgnoreCase(cleanName));
        if (!removed) {
            throw new Exception("Branch '" + cleanName + "' not found.");
        }
        String allCsv = String.join(",", all);
        PermissionConfig config = permRepo.findById("ALL_BRANCHES")
                .orElse(new PermissionConfig("ALL_BRANCHES", allCsv));
        config.setPermissions(allCsv);
        permRepo.save(config);

        // Also remove from active branches
        List<String> active = new ArrayList<>(getActiveBranches());
        active.removeIf(b -> b.equalsIgnoreCase(cleanName));
        updateBranchAccess(active, actor);

        log(actor, "DELETE_BRANCH", "Removed storage facility branch: " + cleanName);
    }

    public void renameBranch(String oldName, String newName, String actor) throws Exception {
        if (oldName == null || oldName.isBlank()) {
            throw new Exception("Invalid branch name.");
        }
        if (newName == null || newName.trim().length() < 2) {
            throw new Exception("New branch name must be at least 2 characters.");
        }
        String oldClean = oldName.trim();
        String newClean = newName.trim();
        if ("Colombo Central".equalsIgnoreCase(oldClean)) {
            throw new Exception("Primary facility 'Colombo Central' cannot be renamed.");
        }
        List<String> all = new ArrayList<>(getAllBranches());
        if (all.stream().noneMatch(b -> b.equalsIgnoreCase(oldClean))) {
            throw new Exception("Branch '" + oldClean + "' not found.");
        }
        if (all.stream().anyMatch(b -> b.equalsIgnoreCase(newClean))) {
            throw new Exception("Branch '" + newClean + "' already exists.");
        }
        // 1. Rename in ALL_BRANCHES registry
        List<String> renamed = all.stream()
                .map(b -> b.equalsIgnoreCase(oldClean) ? newClean : b)
                .collect(Collectors.toList());
        String allCsv = String.join(",", renamed);
        PermissionConfig config = permRepo.findById("ALL_BRANCHES")
                .orElse(new PermissionConfig("ALL_BRANCHES", allCsv));
        config.setPermissions(allCsv);
        permRepo.save(config);

        // 2. Rename in active-access list (preserves its active/restricted state)
        List<String> active = new ArrayList<>(getActiveBranches());
        List<String> renamedActive = active.stream()
                .map(b -> b.equalsIgnoreCase(oldClean) ? newClean : b)
                .collect(Collectors.toList());
        updateBranchAccess(renamedActive, actor);

        // 3. Migrate stocked units so inventory follows the branch (no orphaned stock)
        List<com.blooddonation.spring.model.BloodUnit> units = bloodUnitRepo.findByBranchIgnoreCase(oldClean);
        for (com.blooddonation.spring.model.BloodUnit u : units) {
            u.setBranch(newClean);
            bloodUnitRepo.save(u);
        }

        log(actor, "RENAME_BRANCH", "Renamed storage facility branch '" + oldClean + "' to '" + newClean
                + "' (" + units.size() + " stocked units migrated)");
    }

    public List<String> getActiveBranches() {
        String csv = permRepo.findById("BRANCH_ACCESS")
                .map(PermissionConfig::getPermissions)
                .orElse(DEFAULT_ACTIVE_BRANCHES);
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    public boolean isBranchActive(String branch) {
        if (branch == null || branch.isBlank()) return false;
        return getActiveBranches().stream().anyMatch(b -> b.equalsIgnoreCase(branch.trim()));
    }

    public void updateBranchAccess(List<String> branches, String actor) {
        List<String> all = getAllBranches();
        List<String> validBranches = (branches == null) ? List.of() : branches.stream()
                .filter(b -> all.stream().anyMatch(ab -> ab.equalsIgnoreCase(b.trim())))
                .collect(Collectors.toList());
        String csv = validBranches.isEmpty() ? DEFAULT_ACTIVE_BRANCHES : String.join(",", validBranches);
        PermissionConfig config = permRepo.findById("BRANCH_ACCESS")
                .orElse(new PermissionConfig("BRANCH_ACCESS", csv));
        config.setPermissions(csv);
        permRepo.save(config);
        log(actor, "UPDATE_BRANCH_ACCESS", "Updated storage facility branch access: " + csv);
    }

    public List<UserAccount> getAllUsers() {
        return userRepo.findAll();
    }

    public java.util.Optional<UserAccount> findByDonorId(String donorId) {
        return userRepo.findByDonorId(donorId);
    }

    public UserAccount createUser(String username, String password, String fullName, UserRole role, String hospital) throws Exception {
        if (username == null || username.trim().length() < 3) throw new Exception("Username must be at least 3 characters");
        String clean = username.trim().toLowerCase();
        if (userRepo.existsByUsername(clean) || userRepo.existsByUsername(username.trim())) throw new Exception("Username already exists");
        validatePassword(password);
        if (fullName == null || fullName.trim().length() < 2) throw new Exception("Full name is required");
        if (role == null) throw new Exception("Role is required");
        UserAccount u = new UserAccount();
        u.setUsername(clean);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(fullName.trim());
        u.setRole(role);
        u.setHospital(hospital);
        UserAccount saved = userRepo.save(u);
        log.info("User created: {} with role {}", clean, role);
        return saved;
    }

    public void validatePassword(String password) throws Exception {
        if (password == null || password.length() < 8) {
            throw new Exception("Password must be at least 8 characters (real-world policy)");
        }
    }

    /**
     * Keep donor login in sync when they edit their own name/phone:
     * username follows name, password follows phone number.
     */
    public void updateDonorCredentials(String donorId, String newName, String newPhone) throws Exception {
        UserAccount acc = userRepo.findByDonorId(donorId).orElse(null);
        if (acc == null) return;
        String cleanName = newName == null ? "" : newName.trim().toLowerCase();
        if (!cleanName.isEmpty() && !cleanName.equals(acc.getUsername())) {
            if (cleanName.length() < 3) throw new Exception("Name is too short to use as a login username.");
            if (userRepo.existsByUsername(cleanName)) {
                throw new Exception("This name is already taken as a login username. Please use a different name.");
            }
            acc.setUsername(cleanName);
        }
        String digits = newPhone == null ? "" : newPhone.replaceAll("\\D", "");
        if (!digits.isEmpty() && !encoder.matches(digits, acc.getPasswordHash())) {
            if (digits.length() < 9) throw new Exception("Phone number is too short.");
            acc.setPasswordHash(encoder.encode(digits));
        }
        if (newName != null && !newName.isBlank()) acc.setFullName(newName.trim());
        userRepo.save(acc);
    }

    /**
     * Self-registered donors get an automatic login account:
     * username = registered name (lowercase), password = registered phone number.
     * Returns the final username (suffixed on collision) so the UI can show it.
     */
    public String createDonorAccount(Donor donor) throws Exception {
        if (donor.getId() != null && userRepo.findByDonorId(donor.getId()).isPresent()) {
            return userRepo.findByDonorId(donor.getId()).get().getUsername();
        }
        String base = donor.getName() == null ? "" : donor.getName().trim().toLowerCase();
        String phone = donor.getPhone() == null ? "" : donor.getPhone().replaceAll("\\D", "");
        if (phone.length() < 9) throw new Exception("Valid phone number is required for login account creation");
        if (base.length() < 3) base = (base + "-" + phone.substring(Math.max(0, phone.length() - 4))).trim();
        if (base.length() > 40) base = base.substring(0, 40);
        String username = base;
        if (userRepo.existsByUsername(username)) {
            username = base + "-" + phone.substring(Math.max(0, phone.length() - 4));
        }
        if (userRepo.existsByUsername(username)) {
            username = base + "-" + phone.substring(Math.max(0, phone.length() - 4))
                    + (100 + new java.util.Random().nextInt(900));
        }
        if (username.length() > 50) username = username.substring(0, 50);
        if (userRepo.existsByUsername(username)) throw new Exception("Could not generate a unique login username");
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(phone));
        u.setFullName(donor.getName().trim());
        u.setRole(UserRole.DONOR);
        u.setDonorId(donor.getId());
        userRepo.save(u);
        log.info("Donor login account created: {} (donor {})", username, donor.getId());
        return username;
    }

    public void log(String username, String action, String details) {
        auditRepo.save(new AuditLog(username, action, details));
    }

    public List<AuditLog> getRecentAuditLogs() {
        return auditRepo.findTop50ByOrderByCreatedAtDesc();
    }

    public String getPortalPath(UserRole role) {
        return switch (role) {
            case RECIPIENT -> "/portal/recipient";
            case DONOR -> "/portal/donor";
            case HOSPITAL_COORDINATOR -> "/portal/hospital";
            case QA_OFFICER -> "/portal/qa";
            case DELIVERY_OFFICER -> "/portal/delivery";
            case BLOOD_BANK_MANAGER -> "/portal/manager";
            case IT_ADMIN -> "/portal/it";
        };
    }

    /** Self-registered donors land on their own profile only — nothing else. */
    public String getHomePath(UserAccount user) {
        if (user != null && user.getDonorId() != null && !user.getDonorId().isBlank()) {
            return "/portal/donor";
        }
        return getPortalPath(user.getRole());
    }

    public List<String> getModulesForRole(UserRole role) {
        String perms = permRepo.findById(role.name())
                .map(PermissionConfig::getPermissions)
                .orElse(DEFAULT_PERMISSIONS.get(role));
        return Arrays.stream(perms.split(",")).collect(Collectors.toList());
    }

    public UserAccount toggleUserStatus(String id) throws Exception {
        UserAccount user = userRepo.findById(id).orElseThrow(() -> new Exception("User not found"));
        user.setActive(!user.isActive());
        return userRepo.save(user);
    }

    public UserAccount updateUser(String id, String fullName, UserRole role, String hospital, String newPassword) throws Exception {
        UserAccount user = userRepo.findById(id).orElseThrow(() -> new Exception("User not found"));
        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName.trim());
        if (role != null) user.setRole(role);
        user.setHospital(hospital);
        if (newPassword != null && !newPassword.isBlank()) {
            validatePassword(newPassword);
            user.setPasswordHash(encoder.encode(newPassword));
        }
        return userRepo.save(user);
    }

    public void deleteUser(String id, String currentUsername) throws Exception {
        UserAccount user = userRepo.findById(id).orElseThrow(() -> new Exception("User not found"));
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new Exception("You cannot delete your own currently logged-in account!");
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new Exception("The root admin account cannot be deleted!");
        }
        userRepo.delete(user);
    }
}
