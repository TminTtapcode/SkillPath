package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LearningMigrationUpgradeIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");

    @Test
    void upgradesV13ToV16WithBilingualVersionedCatalogAndConstraints() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("13")).load().migrate();
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("16"))
                .load().migrate().targetSchemaVersion).isEqualTo("16");
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*),SUM(v.estimated_minutes) FROM learning_sequence_items i JOIN task_template_versions v ON v.id=i.task_template_version_id WHERE i.sequence_id=14001")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(3);
                assertThat(rows.getInt(2)).isEqualTo(30);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM task_template_translations WHERE locale='vi-VN'")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(3);
            }
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO learning_sequence_items(sequence_id,position,task_template_version_id) VALUES(14001,1,13002)"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO task_template_knowledge(task_template_version_id,graph_version_id,knowledge_node_id,mapping_role,dimension,weight) VALUES(13001,1,1002,'PRIMARY','UNDERSTANDING',1.2)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
