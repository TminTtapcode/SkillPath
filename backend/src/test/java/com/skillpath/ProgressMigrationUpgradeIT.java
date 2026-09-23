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
class ProgressMigrationUpgradeIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("skillpath").withUsername("skillpath").withPassword("integration-only");
    @Test void upgradesV11ToV13AndCreatesProgressReviewSchema() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).locations("classpath:db/migration").target(MigrationVersion.fromVersion("11")).load().migrate();
        assertThat(Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).locations("classpath:db/migration").target(MigrationVersion.fromVersion("13")).load().migrate().targetSchemaVersion).isEqualTo("13");
        try(Connection connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());Statement statement=connection.createStatement()){
            assertThat(table(statement,"knowledge_evidence")).isEqualTo(1);assertThat(table(statement,"user_knowledge")).isEqualTo(1);assertThat(table(statement,"review_schedules")).isEqualTo(1);assertThat(table(statement,"user_misconceptions")).isEqualTo(1);
            try(ResultSet result=statement.executeQuery("SELECT COUNT(*) FROM misconception_definitions")){result.next();assertThat(result.getInt(1)).isEqualTo(3);}
        }
    }
    private int table(Statement statement,String name)throws Exception{try(ResultSet result=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='"+name+"'")){result.next();return result.getInt(1);}}
}
