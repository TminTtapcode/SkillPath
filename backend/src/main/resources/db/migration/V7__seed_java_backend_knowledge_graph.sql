INSERT INTO knowledge_graph_versions (
    id, curriculum_key, version_label, status, version, validated_at, published_at,
    created_at, updated_at
) VALUES (
    1, 'JAVA_BACKEND', '1.0.0', 'PUBLISHED', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
);

INSERT INTO knowledge_nodes (
    id, graph_version_id, slug, name, description, category, difficulty,
    estimated_minutes, status, metadata, created_at, updated_at
) VALUES
    (1001, 1, 'programming-fundamentals', 'Programming Fundamentals', 'Write and reason about variables, control flow, functions, and basic program decomposition.', 'FOUNDATIONS', 1, 180, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1002, 1, 'command-line', 'Command Line', 'Navigate files, run programs, and inspect processes safely from a command-line environment.', 'FOUNDATIONS', 1, 90, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1003, 1, 'git', 'Git Fundamentals', 'Create commits, inspect history, branch, merge, and collaborate without losing work.', 'TOOLING', 1, 120, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1004, 1, 'http', 'HTTP Fundamentals', 'Explain requests, responses, methods, status codes, headers, caching, and stateless communication.', 'WEB', 2, 150, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1005, 1, 'sql', 'Relational Data and SQL', 'Model relational data and write correct filtered, joined, aggregated, and transactional SQL queries.', 'DATA', 2, 240, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1006, 1, 'java-language', 'Java Language', 'Build and run typed Java programs using core syntax, methods, classes, and standard tooling.', 'JAVA', 2, 300, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1007, 1, 'object-oriented-design', 'Object-Oriented Design', 'Model cohesive objects with explicit responsibilities, encapsulation, interfaces, and composition.', 'JAVA', 3, 240, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1008, 1, 'collections-generics', 'Collections and Generics', 'Select and use Java collections and generic types with correct equality and iteration behavior.', 'JAVA', 3, 210, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1009, 1, 'exceptions', 'Exception Handling', 'Design predictable error paths and use checked, unchecked, and domain exceptions appropriately.', 'JAVA', 2, 120, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1010, 1, 'unit-testing', 'Unit Testing', 'Write isolated, deterministic tests that verify behavior and important boundary cases.', 'QUALITY', 2, 180, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1011, 1, 'rest-api-design', 'REST API Design', 'Design resource-oriented HTTP APIs with validation, stable contracts, and consistent errors.', 'WEB', 3, 210, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1012, 1, 'spring-boot', 'Spring Boot', 'Implement layered Spring Boot services with dependency injection, configuration, and REST controllers.', 'SPRING', 3, 300, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1013, 1, 'jpa-persistence', 'JPA Persistence', 'Map aggregates to relational storage and implement transactional persistence with explicit boundaries.', 'DATA', 4, 300, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1014, 1, 'validation-errors', 'Validation and Error Contracts', 'Validate transport input and expose safe, consistent, machine-readable failure responses.', 'BACKEND', 3, 150, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1015, 1, 'spring-security', 'Authentication and Authorization', 'Protect backend operations with authenticated identity, authorization, sessions, and common web defenses.', 'SECURITY', 4, 300, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1016, 1, 'integration-testing', 'Integration Testing', 'Verify API, database, migration, security, and transaction behavior in production-like test environments.', 'QUALITY', 4, 240, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1017, 1, 'docker-deployment', 'Docker and Deployment Basics', 'Package and run a backend application with reproducible configuration, health checks, and database dependencies.', 'OPERATIONS', 3, 240, 'ACTIVE', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO knowledge_relations (
    id, graph_version_id, source_node_id, target_node_id, relation_type, strength,
    status, rationale, created_at, updated_at
) VALUES
    (2001, 1, 1001, 1006, 'PREREQUISITE', 1.0000, 'ACTIVE', 'Programming constructs precede Java-specific implementation.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2002, 1, 1002, 1003, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Basic shell navigation supports practical Git use.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2003, 1, 1006, 1007, 'PREREQUISITE', 1.0000, 'ACTIVE', 'Java syntax and classes precede object-oriented design practice.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2004, 1, 1006, 1008, 'PREREQUISITE', 1.0000, 'ACTIVE', 'Core Java types precede collections and generics.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2005, 1, 1006, 1009, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Java control flow and methods precede exception design.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2006, 1, 1006, 1010, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Executable Java code is required before unit testing it.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2007, 1, 1004, 1011, 'PREREQUISITE', 1.0000, 'ACTIVE', 'REST contracts are built on HTTP semantics.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2008, 1, 1007, 1012, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Spring services require sound object boundaries.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2009, 1, 1011, 1012, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Spring REST implementation follows API design.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2010, 1, 1005, 1013, 'PREREQUISITE', 1.0000, 'ACTIVE', 'Relational modeling and SQL precede ORM persistence.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2011, 1, 1012, 1013, 'PREREQUISITE', 0.9000, 'ACTIVE', 'JPA is integrated through the Spring application boundary.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2012, 1, 1009, 1014, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Exception semantics support consistent API error contracts.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2013, 1, 1011, 1014, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Validation errors are part of the REST contract.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2014, 1, 1012, 1014, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Spring transport boundaries host validation and mapping.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2015, 1, 1004, 1015, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Web security depends on HTTP and browser behavior.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2016, 1, 1012, 1015, 'PREREQUISITE', 1.0000, 'ACTIVE', 'Spring Security is applied to a Spring application.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2017, 1, 1010, 1016, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Integration tests extend deterministic testing fundamentals.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2018, 1, 1013, 1016, 'PREREQUISITE', 0.9000, 'ACTIVE', 'Database integration behavior must exist before it is tested.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2019, 1, 1015, 1016, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Security boundaries require integration verification.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2020, 1, 1002, 1017, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Container workflows rely on basic command-line operation.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2021, 1, 1012, 1017, 'PREREQUISITE', 0.9000, 'ACTIVE', 'The application must run before it can be packaged.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2022, 1, 1013, 1017, 'PREREQUISITE', 0.8000, 'ACTIVE', 'Deployment must account for the database dependency.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (2023, 1, 1003, 1017, 'RELATED', 0.6000, 'ACTIVE', 'Version control supports delivery but does not gate container learning.', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO goal_knowledge (
    graph_version_id, goal_template_id, knowledge_node_id, relevance_weight,
    required_mastery, is_terminal, created_at, updated_at
) VALUES
    (1, 1, 1001, 0.7000, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1002, 0.5000, 0.7500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1003, 0.6000, 0.7500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1004, 0.8500, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1005, 0.9000, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1006, 1.0000, 0.8500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1007, 0.9000, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1008, 0.8000, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1009, 0.7000, 0.7500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1010, 0.8000, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1011, 1.0000, 0.8500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1012, 1.0000, 0.8500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1013, 1.0000, 0.8500, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1014, 0.8500, 0.8000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1015, 0.9500, 0.8000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1016, 0.9500, 0.8000, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (1, 1, 1017, 0.7500, 0.7500, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO knowledge_version_events (
    graph_version_id, event_type, actor_user_id, correlation_id, from_status,
    to_status, summary, created_at
) VALUES (
    1, 'PUBLISHED', NULL, 'flyway-v7', 'VALIDATED', 'PUBLISHED',
    JSON_OBJECT('source', 'canonical-seed', 'validatorFixture', 'java-backend-v1'),
    UTC_TIMESTAMP(6)
);
