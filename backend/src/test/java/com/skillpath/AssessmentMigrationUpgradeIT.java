package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AssessmentMigrationUpgradeIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath")
            .withUsername("skillpath")
            .withPassword("integration-only");

    @Test
    void upgradesPhaseTwoSchemaFromV7ToV9() throws Exception {
        Flyway phaseTwo = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load();
        assertThat(phaseTwo.migrate().targetSchemaVersion).isEqualTo("7");

        Flyway phaseThree = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();
        assertThat(phaseThree.migrate().targetSchemaVersion).isEqualTo("9");

        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) FROM question_versions WHERE status = 'ACTIVE'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(8);
        }
    }
}
