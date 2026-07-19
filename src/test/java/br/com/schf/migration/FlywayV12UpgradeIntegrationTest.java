package br.com.schf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayV12UpgradeIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_flyway_upgrade_test")
        .withUsername("schf")
        .withPassword("test");

    @Test
    void upgradesExistingV11SchemaToV12() throws SQLException {
        var v11 = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .target(MigrationVersion.fromVersion("11"))
            .load();

        assertThat(v11.migrate().targetSchemaVersion).isEqualTo("11");

        try (var connection = connection()) {
            assertThat(columnExists(connection, "payables", "counterparty_id")).isFalse();
            assertThat(columnIsNullable(connection, "payables", "supplier_id")).isFalse();
        }

        var latest = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();

        assertThat(latest.migrate().targetSchemaVersion).isEqualTo("12");

        try (var connection = connection()) {
            assertThat(columnExists(connection, "payables", "counterparty_id")).isTrue();
            assertThat(columnIsNullable(connection, "payables", "supplier_id")).isTrue();
            assertThat(referencedTable(connection, "payables", "counterparty_id"))
                .isEqualTo("unresolved_legacy_references");
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean columnIsNullable(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT is_nullable = 'YES'
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private String referencedTable(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT ccu.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.constraint_schema = kcu.constraint_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                 AND tc.constraint_schema = ccu.constraint_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                  AND tc.table_name = ?
                  AND kcu.column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }
}
