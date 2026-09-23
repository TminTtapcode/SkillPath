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
class ReplanLeaseMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");

    @Test void pendingRequestSurvivesV23ToV24AndLeaseConstraintIsEnforced()throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("23"))
                .load().migrate();
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            statement.executeUpdate("INSERT INTO users(id,display_name,timezone,created_at,updated_at) "
                    +"VALUES(9201,'Replan upgrade','Asia/Ho_Chi_Minh',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO user_goals(user_id,goal_template_id,target_date,timezone,"
                    +"default_daily_minutes,status,created_at,updated_at) VALUES"
                    +"(9201,1,DATE_ADD(CURRENT_DATE(),INTERVAL 90 DAY),'Asia/Ho_Chi_Minh',60,"
                    +"'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO planner_replan_requests(attempt_kind,attempt_id,"
                    +"source_event_id,user_id,goal_id,graph_version_id,status,attempt_count,available_at,created_at) "
                    +"SELECT 'TASK_CHECK',920001,920002,9201,id,1,'PENDING',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) "
                    +"FROM user_goals WHERE user_id=9201");
        }
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate().targetSchemaVersion)
                .isEqualTo("26");
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            try(var row=statement.executeQuery("SELECT status,attempt_count,updated_at,locked_by "
                    +"FROM planner_replan_requests WHERE attempt_id=920001")){
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("PENDING");
                assertThat(row.getInt(2)).isZero();
                assertThat(row.getTimestamp(3)).isNotNull();
                assertThat(row.getString(4)).isNull();
            }
            assertThatThrownBy(()->statement.executeUpdate("UPDATE planner_replan_requests "
                    +"SET status='PROCESSING' WHERE attempt_id=920001"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
