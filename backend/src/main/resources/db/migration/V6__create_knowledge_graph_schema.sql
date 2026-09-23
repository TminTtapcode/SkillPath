CREATE TABLE knowledge_graph_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    curriculum_key VARCHAR(100) NOT NULL,
    version_label VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_curriculum_key VARCHAR(100) GENERATED ALWAYS AS (
        CASE WHEN status = 'PUBLISHED' THEN curriculum_key ELSE NULL END
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    validated_at DATETIME(6) NULL,
    validated_by BIGINT NULL,
    published_at DATETIME(6) NULL,
    published_by BIGINT NULL,
    retired_at DATETIME(6) NULL,
    retired_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_graph_versions PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_graph_version_label UNIQUE (curriculum_key, version_label),
    CONSTRAINT uk_knowledge_graph_published UNIQUE (published_curriculum_key),
    CONSTRAINT fk_knowledge_graph_validated_by FOREIGN KEY (validated_by) REFERENCES users (id),
    CONSTRAINT fk_knowledge_graph_published_by FOREIGN KEY (published_by) REFERENCES users (id),
    CONSTRAINT fk_knowledge_graph_retired_by FOREIGN KEY (retired_by) REFERENCES users (id),
    CONSTRAINT ck_knowledge_graph_version_status CHECK (
        status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'RETIRED')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_nodes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(100) NOT NULL,
    difficulty INT NOT NULL,
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    metadata JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_nodes PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_nodes_version_slug UNIQUE (graph_version_id, slug),
    CONSTRAINT uk_knowledge_nodes_version_id UNIQUE (graph_version_id, id),
    CONSTRAINT fk_knowledge_nodes_version FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT ck_knowledge_nodes_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_knowledge_nodes_minutes CHECK (estimated_minutes > 0),
    CONSTRAINT ck_knowledge_nodes_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_relations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    relation_type VARCHAR(30) NOT NULL,
    strength DECIMAL(5,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
    ) STORED,
    rationale VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_relations PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_relations_version FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT fk_knowledge_relations_source FOREIGN KEY (graph_version_id, source_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT fk_knowledge_relations_target FOREIGN KEY (graph_version_id, target_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT uk_knowledge_relations_active UNIQUE (
        graph_version_id, source_node_id, target_node_id, relation_type, active_marker
    ),
    CONSTRAINT ck_knowledge_relations_not_self CHECK (source_node_id <> target_node_id),
    CONSTRAINT ck_knowledge_relations_strength CHECK (strength BETWEEN 0 AND 1),
    CONSTRAINT ck_knowledge_relations_type CHECK (
        relation_type IN ('PREREQUISITE', 'PART_OF', 'RELATED', 'APPLIED_IN')
    ),
    CONSTRAINT ck_knowledge_relations_status CHECK (status IN ('ACTIVE', 'DEPRECATED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_knowledge_relations_source
    ON knowledge_relations (graph_version_id, source_node_id, relation_type, status);
CREATE INDEX ix_knowledge_relations_target
    ON knowledge_relations (graph_version_id, target_node_id, relation_type, status);

CREATE TABLE goal_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    goal_template_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    relevance_weight DECIMAL(5,4) NOT NULL,
    required_mastery DECIMAL(5,4) NOT NULL,
    is_terminal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_goal_knowledge PRIMARY KEY (id),
    CONSTRAINT uk_goal_knowledge_mapping UNIQUE (
        graph_version_id, goal_template_id, knowledge_node_id
    ),
    CONSTRAINT fk_goal_knowledge_version FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT fk_goal_knowledge_goal FOREIGN KEY (goal_template_id)
        REFERENCES goal_templates (id),
    CONSTRAINT fk_goal_knowledge_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT ck_goal_knowledge_relevance CHECK (relevance_weight BETWEEN 0 AND 1),
    CONSTRAINT ck_goal_knowledge_mastery CHECK (required_mastery BETWEEN 0 AND 1)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_goal_knowledge_goal_version
    ON goal_knowledge (goal_template_id, graph_version_id, is_terminal);

CREATE TABLE knowledge_version_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    summary JSON NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_version_events PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_version_events_version FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT fk_knowledge_version_events_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id),
    CONSTRAINT ck_knowledge_version_events_type CHECK (
        event_type IN ('VALIDATED', 'PUBLISHED', 'RETIRED')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_knowledge_version_events_version_created
    ON knowledge_version_events (graph_version_id, created_at);
