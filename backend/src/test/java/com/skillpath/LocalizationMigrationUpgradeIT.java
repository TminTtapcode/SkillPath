package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class LocalizationMigrationUpgradeIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath")
            .withUsername("skillpath")
            .withPassword("integration-only");

    @Test
    void upgradesPhaseThreeSchemaFromV9ToV11WithCompleteVietnameseSeed() throws Exception {
        Flyway phaseThree = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9"))
                .load();
        assertThat(phaseThree.migrate().targetSchemaVersion).isEqualTo("9");

        Flyway localization = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load();
        assertThat(localization.migrate().targetSchemaVersion).isEqualTo("11");

        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            assertThat(count(statement, "goal_template_translations")).isEqualTo(1);
            assertThat(count(statement, "knowledge_node_translations")).isEqualTo(17);
            assertThat(count(statement, "question_version_translations")).isEqualTo(8);

            try (ResultSet result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM question_version_translations translation
                    JOIN question_versions version ON version.id = translation.question_version_id
                    WHERE JSON_LENGTH(translation.options) = JSON_LENGTH(version.options)
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(8);
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO goal_template_translations
                        (goal_template_id, locale, display_name, description, created_at)
                    VALUES (1, 'fr-FR', 'Java', 'Java', UTC_TIMESTAMP(6))
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    private int count(Statement statement, String table) throws Exception {
        if (!java.util.Set.of(
                        "goal_template_translations",
                        "knowledge_node_translations",
                        "question_version_translations")
                .contains(table)) {
            throw new IllegalArgumentException("Unexpected table");
        }
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
