package com.blooddonation.spring.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Render-friendly DataSource.
 *
 * Render (and most cloud providers) expose a database URL that starts with
 * "postgresql://" or "postgres://" instead of "jdbc:postgresql://".
 * The PostgreSQL JDBC driver refuses such a URL ("claims to not accept jdbcUrl"),
 * which used to make the first deploy fail and forced a manual edit of the
 * DB_URL environment variable in the Render dashboard.
 *
 * This class normalises the URL before HikariCP sees it:
 *
 *   postgres://user:pass@host:5432/db        -> jdbc:postgresql://host:5432/db
 *   postgresql://user:pass@host/db?sslmode=x -> jdbc:postgresql://host/db?sslmode=x
 *
 * The user/password embedded in the URL are extracted and passed as normal
 * credentials (URL-decoded, so special characters in the password are fine).
 * If the app is running locally against SQL Server (jdbc:sqlserver://...) the
 * URL is passed through untouched, so local development behaves exactly as before.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        String rawUrl = properties.getUrl();
        String username = properties.getUsername();
        String password = properties.getPassword();

        if (StringUtils.hasText(rawUrl) && isHerokuStyleUrl(rawUrl)) {
            ParsedUrl parsed = parse(rawUrl);
            rawUrl = parsed.jdbcUrl;
            if (StringUtils.hasText(parsed.username)) {
                username = parsed.username;
            }
            if (parsed.password != null) {
                password = parsed.password;
            }
            System.out.println("DataSource: normalised cloud database URL to "
                    + maskCredentials(rawUrl));
        }

        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setJdbcUrl(rawUrl);

        // Explicit driver selection keeps SQL Server (local) and Postgres (cloud)
        // working from the same build.
        if (StringUtils.hasText(rawUrl)) {
            if (rawUrl.startsWith("jdbc:postgresql:")) {
                dataSource.setDriverClassName("org.postgresql.Driver");
            } else if (rawUrl.startsWith("jdbc:sqlserver:")) {
                dataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            } else if (rawUrl.startsWith("jdbc:h2:")) {
                dataSource.setDriverClassName("org.h2.Driver");
            } else if (rawUrl.startsWith("jdbc:mysql:")) {
                dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            }
        }

        if (username != null) {
            dataSource.setUsername(username);
        }
        if (password != null) {
            dataSource.setPassword(password);
        }
        return dataSource;
    }

    private static boolean isHerokuStyleUrl(String url) {
        return (url.startsWith("postgres://") || url.startsWith("postgresql://"))
                && !url.startsWith("jdbc:");
    }

    private static String maskCredentials(String url) {
        return url.replaceAll("//[^/@]+@", "//***:***@");
    }

    private static ParsedUrl parse(String rawUrl) {
        ParsedUrl result = new ParsedUrl();

        String rest = rawUrl.substring(rawUrl.indexOf("://") + 3);
        String query = "";
        int questionMark = rest.indexOf('?');
        if (questionMark >= 0) {
            query = rest.substring(questionMark);
            rest = rest.substring(0, questionMark);
        }

        int at = rest.lastIndexOf('@');
        String hostAndDb = rest;
        if (at >= 0) {
            String credentials = rest.substring(0, at);
            hostAndDb = rest.substring(at + 1);
            int colon = credentials.indexOf(':');
            if (colon >= 0) {
                result.username = decode(credentials.substring(0, colon));
                result.password = decode(credentials.substring(colon + 1));
            } else {
                result.username = decode(credentials);
            }
        }

        if (!query.contains("sslmode=")) {
            query = query.isEmpty() ? "?sslmode=require" : query + "&sslmode=require";
        }

        result.jdbcUrl = "jdbc:postgresql://" + hostAndDb + query;
        return result;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static final class ParsedUrl {
        private String jdbcUrl;
        private String username;
        private String password;
    }
}
