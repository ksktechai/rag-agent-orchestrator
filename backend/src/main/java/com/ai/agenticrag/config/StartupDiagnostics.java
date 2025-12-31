package com.ai.agenticrag.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class StartupDiagnostics {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartupDiagnostics.class);

    @Bean
    ApplicationRunner showJdbcUrl(DataSource ds) {
        return args -> {
            try (var c = ds.getConnection()) {
                log.info("✅ JDBC URL = {}", c.getMetaData().getURL());
                log.info("✅ DB User  = {}", c.getMetaData().getUserName());
            }
        };
    }
}
