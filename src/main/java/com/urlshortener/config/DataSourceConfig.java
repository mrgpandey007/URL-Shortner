package com.urlshortener.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            // Step 1: Add jdbc: prefix
            String jdbcUrl = databaseUrl
                .replace("postgresql://", "jdbc:postgresql://")
                .replace("postgres://", "jdbc:postgresql://");

            // Step 2: Add port :5432 if missing
            // Format: jdbc:postgresql://user:pass@host/db
            // Need:   jdbc:postgresql://user:pass@host:5432/db
            if (!jdbcUrl.contains(":5432") && !jdbcUrl.contains(":5433")) {
                // Find @ symbol, then find next / after it — insert :5432 before that /
                int atIndex = jdbcUrl.lastIndexOf("@");
                if (atIndex != -1) {
                    int slashAfterAt = jdbcUrl.indexOf("/", atIndex);
                    if (slashAfterAt != -1) {
                        jdbcUrl = jdbcUrl.substring(0, slashAfterAt)
                                + ":5432"
                                + jdbcUrl.substring(slashAfterAt);
                    }
                }
            }

            log.info("JDBC URL constructed successfully");
            config.setJdbcUrl(jdbcUrl);

        } else {
            // Local development fallback
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/urlshortener");
            config.setUsername("postgres");
            config.setPassword("postgres");
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);

        return new HikariDataSource(config);
    }
}
