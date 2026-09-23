package com.skillpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class GoalBudgetMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillpath").withUsername("skillpath")
            .withPassword("integration-only");

    @Test void upgradePreservesExistingGoalsAndAcceptsTwentyButNotNineteen()throws Exception{
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("19"))
                .load().migrate();
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            statement.executeUpdate("INSERT INTO users(id,display_name,timezone,created_at,updated_at) "
                    +"VALUES(9001,'Existing learner','Asia/Ho_Chi_Minh',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            statement.executeUpdate("INSERT INTO user_goals(user_id,goal_template_id,target_date,"
                    +"timezone,default_daily_minutes,status,created_at,updated_at) VALUES"
                    +"(9001,1,DATE_ADD(CURRENT_DATE(),INTERVAL 90 DAY),'Asia/Ho_Chi_Minh',30,"
                    +"'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            assertThatThrownBy(()->statement.executeUpdate("UPDATE user_goals "
                    +"SET default_daily_minutes=20 WHERE user_id=9001"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate().targetSchemaVersion)
                .isEqualTo("20");
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()){
            try(var row=statement.executeQuery("SELECT default_daily_minutes FROM user_goals "
                    +"WHERE user_id=9001")){
                row.next();assertThat(row.getInt(1)).isEqualTo(30);
            }
            statement.executeUpdate("UPDATE user_goals SET default_daily_minutes=20 "
                    +"WHERE user_id=9001");
            assertThatThrownBy(()->statement.executeUpdate("UPDATE user_goals "
                    +"SET default_daily_minutes=19 WHERE user_id=9001"))
                    .isInstanceOf(SQLException.class);
            try(var row=statement.executeQuery("SELECT default_daily_minutes FROM user_goals "
                    +"WHERE user_id=9001")){
                row.next();assertThat(row.getInt(1)).isEqualTo(20);
            }
        }
    }
}
