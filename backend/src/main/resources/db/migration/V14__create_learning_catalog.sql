CREATE TABLE resources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_key VARCHAR(120) NOT NULL UNIQUE,
    provider VARCHAR(120) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    license_code VARCHAR(60) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_learning_resource_source CHECK (source_type IN ('PROJECT_AUTHORED', 'EXTERNAL'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE resource_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    content_ref VARCHAR(255) NOT NULL,
    section_key VARCHAR(120) NOT NULL,
    difficulty INT NOT NULL,
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_learning_resource_version UNIQUE (resource_id, version_number),
    CONSTRAINT fk_learning_resource_version_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT ck_learning_resource_version_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_learning_resource_version_minutes CHECK (estimated_minutes > 0),
    CONSTRAINT ck_learning_resource_version_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE resource_version_translations (
    resource_version_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    PRIMARY KEY (resource_version_id, locale),
    CONSTRAINT fk_learning_resource_translation FOREIGN KEY (resource_version_id) REFERENCES resource_versions(id),
    CONSTRAINT ck_learning_resource_locale CHECK (locale = 'vi-VN')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_resources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_version_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    section_key VARCHAR(120) NOT NULL,
    purpose VARCHAR(120) NOT NULL,
    estimated_minutes INT NOT NULL,
    CONSTRAINT uk_learning_knowledge_resource UNIQUE (resource_version_id, graph_version_id, knowledge_node_id),
    CONSTRAINT fk_learning_knowledge_resource_version FOREIGN KEY (resource_version_id) REFERENCES resource_versions(id),
    CONSTRAINT fk_learning_knowledge_resource_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes(graph_version_id, id),
    CONSTRAINT ck_learning_knowledge_resource_minutes CHECK (estimated_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_key VARCHAR(120) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_template_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    instructions TEXT NOT NULL,
    checklist JSON NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    evaluation_mode VARCHAR(20) NOT NULL,
    difficulty INT NOT NULL,
    estimated_minutes INT NOT NULL,
    min_minutes INT NOT NULL,
    max_minutes INT NOT NULL,
    variant_group_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_source VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_learning_task_template_version UNIQUE (template_id, version_number),
    CONSTRAINT fk_learning_task_template FOREIGN KEY (template_id) REFERENCES task_templates(id),
    CONSTRAINT fk_learning_task_template_graph FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT fk_learning_task_template_resource FOREIGN KEY (resource_version_id) REFERENCES resource_versions(id),
    CONSTRAINT ck_learning_task_activity CHECK (activity_type IN ('LEARN','PRACTICE','RECALL')),
    CONSTRAINT ck_learning_task_evaluation CHECK (evaluation_mode IN ('NONE','SELF_REPORT')),
    CONSTRAINT ck_learning_task_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_learning_task_duration CHECK (min_minutes > 0 AND estimated_minutes BETWEEN min_minutes AND max_minutes),
    CONSTRAINT ck_learning_task_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_learning_task_source CHECK (content_source = 'CURATED')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_learning_template_active_graph ON task_template_versions(graph_version_id, status);

CREATE TABLE task_template_translations (
    task_template_version_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    title VARCHAR(180) NOT NULL,
    instructions TEXT NOT NULL,
    checklist JSON NOT NULL,
    PRIMARY KEY(task_template_version_id, locale),
    CONSTRAINT fk_learning_task_translation FOREIGN KEY (task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT ck_learning_task_locale CHECK (locale = 'vi-VN')
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_template_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_template_version_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    mapping_role VARCHAR(30) NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    weight DECIMAL(5,4) NOT NULL,
    CONSTRAINT uk_learning_task_knowledge UNIQUE(task_template_version_id, knowledge_node_id, dimension),
    CONSTRAINT fk_learning_task_mapping_version FOREIGN KEY(task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT fk_learning_task_mapping_node FOREIGN KEY(graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes(graph_version_id, id),
    CONSTRAINT ck_learning_task_mapping_role CHECK(mapping_role IN ('PRIMARY','SUPPORTING','PREREQUISITE_REVIEW')),
    CONSTRAINT ck_learning_task_mapping_dimension CHECK(dimension IN ('RECOGNITION','UNDERSTANDING','RECALL','APPLICATION')),
    CONSTRAINT ck_learning_task_mapping_weight CHECK(weight > 0 AND weight <= 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE learning_sequences (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sequence_key VARCHAR(120) NOT NULL,
    version_number INT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    title_vi VARCHAR(180) NOT NULL,
    description_vi VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_learning_sequence_version UNIQUE(sequence_key, version_number),
    CONSTRAINT fk_learning_sequence_graph FOREIGN KEY(graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT ck_learning_sequence_status CHECK(status IN ('DRAFT','ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_learning_sequence_active_graph ON learning_sequences(graph_version_id, status);

CREATE TABLE learning_sequence_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sequence_id BIGINT NOT NULL,
    position INT NOT NULL,
    task_template_version_id BIGINT NOT NULL,
    CONSTRAINT uk_learning_sequence_position UNIQUE(sequence_id, position),
    CONSTRAINT uk_learning_sequence_template UNIQUE(sequence_id, task_template_version_id),
    CONSTRAINT fk_learning_sequence_item_sequence FOREIGN KEY(sequence_id) REFERENCES learning_sequences(id),
    CONSTRAINT fk_learning_sequence_item_template FOREIGN KEY(task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT ck_learning_sequence_position CHECK(position > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
