package com.urlshortener.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            // Parse the URI properly
            // Input: postgresql://user:pass@host/db
            URI uri = new URI(databaseUrl
                    .replace("postgresql://", "http://")
                    .replace("postgres://", "http://"));

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath(); // /dbname
            String userInfo = uri.getUserInfo(); // user:pass
            String user = userInfo.split(":")[0];
            String pass = userInfo.split(":")[1];

            String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d%s", host, port, path);

            log.info("Connecting to: jdbc:postgresql://{}:{}{}", host, port, path);

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(user);
            config.setPassword(pass);
        } else {
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/urlshortener");
            config.setUsername("postgres");
            config.setPassword("postgres");
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);

        return new HikariDataSource(config);
    }
}
