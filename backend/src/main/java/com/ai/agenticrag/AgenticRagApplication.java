package com.ai.agenticrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AgenticRag Spring Boot application.
 * This class initializes and starts the application context using Spring Boot's auto-configuration mechanism.
 * The application serves as the foundation for running and managing the AgenticRag system.
 * <p>
 * Key responsibilities:
 * - Bootstrapping the Spring application context.
 * - Enabling dependency injection and component scanning in the application.
 * - Resolving and configuring application-specific beans and services at runtime.
 * <p>
 * The application may be utilized by other components, such as command-line tools or web interfaces,
 * to trigger workflows like data ingestion or processing tasks.
 */
@SpringBootApplication
public class AgenticRagApplication {

    /**
     * The main method serves as the entry point for the AgenticRag Spring Boot application.
     * It initializes and launches the application by invoking the Spring Boot framework.
     *
     * @param args Command-line arguments passed during the application startup. These arguments
     *             can be used for configuring application behavior or passing runtime parameters.
     */
    public static void main(String[] args) {
        SpringApplication.run(AgenticRagApplication.class, args);
    }
}
