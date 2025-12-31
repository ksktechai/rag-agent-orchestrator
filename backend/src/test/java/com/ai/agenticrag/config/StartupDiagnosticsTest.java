package com.ai.agenticrag.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StartupDiagnostics.
 */
@ExtendWith(MockitoExtension.class)
class StartupDiagnosticsTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Test
    void showJdbcUrl_createsApplicationRunner() {
        StartupDiagnostics diagnostics = new StartupDiagnostics();

        ApplicationRunner runner = diagnostics.showJdbcUrl(dataSource);

        assertNotNull(runner);
    }

    @Test
    void showJdbcUrl_logsJdbcUrlAndUser() throws Exception {
        StartupDiagnostics diagnostics = new StartupDiagnostics();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
        when(metaData.getUserName()).thenReturn("testuser");

        ApplicationRunner runner = diagnostics.showJdbcUrl(dataSource);
        runner.run(null);

        verify(dataSource).getConnection();
        verify(metaData).getURL();
        verify(metaData).getUserName();
        verify(connection).close();
    }

    @Test
    void showJdbcUrl_closesConnection() throws Exception {
        StartupDiagnostics diagnostics = new StartupDiagnostics();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/db");
        when(metaData.getUserName()).thenReturn("user");

        ApplicationRunner runner = diagnostics.showJdbcUrl(dataSource);
        runner.run(null);

        verify(connection).close();
    }

    @Test
    void showJdbcUrl_handlesNullArgs() throws Exception {
        StartupDiagnostics diagnostics = new StartupDiagnostics();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:h2:mem:test");
        when(metaData.getUserName()).thenReturn("sa");

        ApplicationRunner runner = diagnostics.showJdbcUrl(dataSource);

        // Should not throw when args is null
        assertDoesNotThrow(() -> runner.run(null));
    }

    @Test
    void showJdbcUrl_handlesEmptyArgs() throws Exception {
        StartupDiagnostics diagnostics = new StartupDiagnostics();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:postgresql://host:5432/db");
        when(metaData.getUserName()).thenReturn("admin");

        ApplicationRunner runner = diagnostics.showJdbcUrl(dataSource);

        // Should work with any application arguments (including null)
        assertDoesNotThrow(() -> runner.run(mock(org.springframework.boot.ApplicationArguments.class)));
    }
}
