package com.ai.agenticrag.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class StartupDiagnostics {

    @Bean
    ApplicationRunner showJdbcUrl(DataSource ds) {
        return args -> {
            try (var c = ds.getConnection()) {
                System.out.println("✅ JDBC URL = " + c.getMetaData().getURL());
                System.out.println("✅ DB User  = " + c.getMetaData().getUserName());
            }
        };
    }
}
