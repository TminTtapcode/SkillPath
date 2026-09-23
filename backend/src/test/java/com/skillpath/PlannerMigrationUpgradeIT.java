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
class PlannerMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");

    @Test void upgradesV16ToV19WithoutChangingExistingLearningHistory()throws Exception{
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("16"))
                .load().migrate();
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("19"))
                .load().migrate().targetSchemaVersion)
                .isEqualTo("19");
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
            var statement=connection.createStatement()){
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM task_template_versions "
                    +"WHERE graph_version_id=1 AND status='ACTIVE'")){
                rows.next();assertThat(rows.getInt(1)).isEqualTo(6);
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM learning_sequence_items "
                    +"WHERE sequence_id=14001")){
                rows.next();assertThat(rows.getInt(1)).isEqualTo(3);
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM planner_policies "
                    +"WHERE version='planner-v1'")){
                rows.next();assertThat(rows.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(()->statement.executeUpdate("INSERT INTO daily_plans "
                    +"(user_id,goal_id,learning_day,timezone,budget_minutes,revision,snapshot_id,status,"
                    +"outcome_code,created_at) VALUES(1,1,CURRENT_DATE(),'Asia/Ho_Chi_Minh',0,1,1,"
                    +"'CURRENT','PLANNED',UTC_TIMESTAMP(6))"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
