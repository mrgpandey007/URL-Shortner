package com.urlshortener.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Converts Render's DATABASE_URL format to JDBC format Spring understands.
 *
 * Render provides: postgresql://user:pass@host/db
 * Spring needs:    jdbc:postgresql://user:pass@host/db
 */
@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/urlshortener}")
    private String fallbackUrl;

    @Value("${SPRING_DATASOURCE_USERNAME:postgres}")
    private String username;

    @Value("${SPRING_DATASOURCE_PASSWORD:postgres}")
    private String password;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            // Render provides postgresql:// — convert to jdbc:postgresql://
            String jdbcUrl = databaseUrl
                .replace("postgresql://", "jdbc:postgresql://")
                .replace("postgres://", "jdbc:postgresql://");

            log.info("Using Render DATABASE_URL (converted to JDBC format)");
            config.setJdbcUrl(jdbcUrl);
            // Username and password are embedded in the URL — no need to set separately
        } else {
            log.info("Using fallback datasource config");
            config.setJdbcUrl(fallbackUrl);
            config.setUsername(username);
            config.setPassword(password);
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);

        return new HikariDataSource(config);
    }
}
