CREATE TABLE goal_template_translations (
    goal_template_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_goal_template_translations PRIMARY KEY (goal_template_id, locale),
    CONSTRAINT fk_goal_template_translations_template FOREIGN KEY (goal_template_id)
        REFERENCES goal_templates (id),
    CONSTRAINT ck_goal_template_translations_locale CHECK (locale IN ('vi-VN'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_node_translations (
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_node_translations PRIMARY KEY (
        graph_version_id, knowledge_node_id, locale
    ),
    CONSTRAINT fk_knowledge_node_translations_node FOREIGN KEY (
        graph_version_id, knowledge_node_id
    ) REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT ck_knowledge_node_translations_locale CHECK (locale IN ('vi-VN'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_knowledge_node_translations_locale
    ON knowledge_node_translations (locale, graph_version_id, knowledge_node_id);

CREATE TABLE question_version_translations (
    question_version_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    prompt VARCHAR(3000) NOT NULL,
    options JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_question_version_translations PRIMARY KEY (question_version_id, locale),
    CONSTRAINT fk_question_version_translations_version FOREIGN KEY (question_version_id)
        REFERENCES question_versions (id),
    CONSTRAINT ck_question_version_translations_locale CHECK (locale IN ('vi-VN'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_question_version_translations_locale
    ON question_version_translations (locale, question_version_id);
