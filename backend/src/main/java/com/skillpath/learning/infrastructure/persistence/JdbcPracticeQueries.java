package com.skillpath.learning.infrastructure.persistence;

import com.skillpath.learning.application.PracticeQueries;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JdbcPracticeQueries implements PracticeQueries {

    private final JdbcTemplate jdbc;

    public JdbcPracticeQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PracticeExercise> taskPractices(long taskTemplateVersionId) {
        String sql = """
                SELECT id, position, prompt, starter_code, expected_output
                FROM task_template_practices
                WHERE task_template_version_id = ?
                ORDER BY position ASC
                """;
        return jdbc.query(sql, (rs, row) -> new PracticeExercise(
                rs.getLong("id"),
                rs.getInt("position"),
                rs.getString("prompt"),
                rs.getString("starter_code"),
                rs.getString("expected_output")
        ), taskTemplateVersionId);
    }
}
