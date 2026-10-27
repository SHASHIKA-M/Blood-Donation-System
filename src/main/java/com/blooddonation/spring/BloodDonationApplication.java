package com.blooddonation.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class BloodDonationApplication {
    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        configureSqlServerAuthenticationLibrary();
        SpringApplication.run(BloodDonationApplication.class, args);
    }

    private static void configureSqlServerAuthenticationLibrary() {
        // Windows dev machines: always attempt to load the integrated-auth DLL
        // (needed whenever the JDBC URL contains integratedSecurity=true).
        // Linux/Docker: skipped automatically. Prod SQL-auth URLs simply don't
        // trigger native auth, so loading the DLL is harmless there.
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return;
        }

        String dllName = "mssql-jdbc_auth-12.4.2.x64.dll";
        Path[] candidatePaths = new Path[]{
                Path.of(System.getProperty("user.dir"), "native", dllName),
                Path.of(System.getProperty("user.dir"), "target", "native", dllName),
                Path.of(System.getProperty("user.dir"), "target", "classes", "native", dllName),
                Path.of(System.getProperty("user.dir"), "src", "main", "resources", "native", dllName),
                Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "microsoft", "sqlserver", "mssql-jdbc_auth", "12.4.2.x64", dllName)
        };

        Path nativeLibrary = null;
        for (Path candidate : candidatePaths) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                nativeLibrary = candidate;
                break;
            }
        }

        // If not found directly on disk, try extracting from classpath resource
        if (nativeLibrary == null) {
            try (var is = BloodDonationApplication.class.getResourceAsStream("/native/" + dllName)) {
                if (is != null) {
                    Path tempDll = Path.of(System.getProperty("java.io.tmpdir"), dllName);
                    Files.copy(is, tempDll, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    nativeLibrary = tempDll;
                }
            } catch (Exception ignored) {
            }
        }

        if (nativeLibrary != null && Files.isRegularFile(nativeLibrary)) {
            try {
                System.load(nativeLibrary.toAbsolutePath().toString());
                // Eagerly initialize AuthenticationJNI in this classloader before HikariCP connects
                Class.forName("com.microsoft.sqlserver.jdbc.AuthenticationJNI");
                System.out.println("SQL Server integrated authentication initialized successfully with " + nativeLibrary.getFileName());
            } catch (Throwable ex) {
                if (ex.getMessage() == null || !ex.getMessage().contains("already loaded")) {
                    System.err.println("Notice: Could not load SQL Server native library: " + ex.getMessage());
                    System.err.println("Fix: run with Java 17 (Project SDK / ALTERNATIVE_JRE_PATH) and "
                            + "ensure -Djava.library.path points at the folder containing "
                            + nativeLibrary.getFileName());
                }
            }
        }
    }
}
