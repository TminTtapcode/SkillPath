package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AdaptiveMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath")
            .withPassword("integration-only");

    @Test void v20HistorySurvivesForwardTaskCheckSchemaAndSeed()throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("20"))
                .load().migrate();
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            statement.executeUpdate("INSERT INTO users(id,display_name,timezone,created_at,updated_at) "
                    +"VALUES(9101,'P7 upgrade learner','Asia/Ho_Chi_Minh',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO user_goals(user_id,goal_template_id,target_date,timezone,"
                    +"default_daily_minutes,status,created_at,updated_at) VALUES"
                    +"(9101,1,DATE_ADD(CURRENT_DATE(),INTERVAL 90 DAY),'Asia/Ho_Chi_Minh',20,"
                    +"'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        }
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate().targetSchemaVersion)
                .isEqualTo("25");
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            try(var row=statement.executeQuery("SELECT default_daily_minutes FROM user_goals WHERE user_id=9101")){
                row.next();assertThat(row.getInt(1)).isEqualTo(20);
            }
            try(var row=statement.executeQuery("SELECT COUNT(*) FROM task_check_definitions WHERE graph_version_id=1")){
                row.next();assertThat(row.getInt(1)).isEqualTo(2);
            }
            try(var row=statement.executeQuery("SELECT COUNT(*) FROM task_template_versions WHERE evaluation_mode='OBJECTIVE'")){
                row.next();assertThat(row.getInt(1)).isEqualTo(2);
            }
        }
    }
}
