INSERT INTO questions (id, question_key, created_at, updated_at) VALUES
    (3001, 'java-backend-diagnostic-control-flow', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3002, 'java-backend-diagnostic-git-history', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3003, 'java-backend-diagnostic-http-methods', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3004, 'java-backend-diagnostic-sql-grouping', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3005, 'java-backend-diagnostic-java-types', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3006, 'java-backend-diagnostic-composition', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3007, 'java-backend-diagnostic-collections', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3008, 'java-backend-diagnostic-unit-tests', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO question_versions (
    id, question_id, version_number, type, prompt, difficulty, estimated_seconds,
    scoring_strategy, options, answer_key, rubric, status, source, created_at, updated_at
) VALUES
    (3101, 3001, 1, 'SINGLE_CHOICE',
     'Which control-flow construct is best suited to repeat an operation while a condition remains true?',
     1, 45, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'if', 'label', 'if statement'),
        JSON_OBJECT('id', 'while', 'label', 'while loop'),
        JSON_OBJECT('id', 'class', 'label', 'class declaration'),
        JSON_OBJECT('id', 'import', 'label', 'import statement')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('while')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3102, 3002, 1, 'MULTIPLE_CHOICE',
     'Which Git actions help inspect existing history without creating a new commit? Select all that apply.',
     1, 60, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'log', 'label', 'git log'),
        JSON_OBJECT('id', 'show', 'label', 'git show'),
        JSON_OBJECT('id', 'commit', 'label', 'git commit'),
        JSON_OBJECT('id', 'init', 'label', 'git init')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('log', 'show')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3103, 3003, 1, 'SINGLE_CHOICE',
     'Which HTTP method is conventionally used to retrieve a resource without requesting a state change?',
     1, 45, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'get', 'label', 'GET'),
        JSON_OBJECT('id', 'post', 'label', 'POST'),
        JSON_OBJECT('id', 'patch', 'label', 'PATCH'),
        JSON_OBJECT('id', 'delete', 'label', 'DELETE')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('get')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3104, 3004, 1, 'MULTIPLE_CHOICE',
     'Which SQL clauses are normally involved when calculating a count per category? Select all that apply.',
     2, 60, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'count', 'label', 'COUNT(...)'),
        JSON_OBJECT('id', 'group-by', 'label', 'GROUP BY'),
        JSON_OBJECT('id', 'drop-table', 'label', 'DROP TABLE'),
        JSON_OBJECT('id', 'grant', 'label', 'GRANT')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('count', 'group-by')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3105, 3005, 1, 'SINGLE_CHOICE',
     'In Java, which declaration prevents a local variable reference from being reassigned after initialization?',
     2, 45, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'final', 'label', 'final'),
        JSON_OBJECT('id', 'static', 'label', 'static'),
        JSON_OBJECT('id', 'public', 'label', 'public'),
        JSON_OBJECT('id', 'throws', 'label', 'throws')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('final')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3106, 3006, 1, 'MULTIPLE_CHOICE',
     'Which choices generally support object composition with explicit responsibilities? Select all that apply.',
     3, 60, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'constructor-dependency', 'label', 'Pass a dependency through a constructor'),
        JSON_OBJECT('id', 'small-interface', 'label', 'Depend on a focused interface'),
        JSON_OBJECT('id', 'global-state', 'label', 'Store every dependency in global mutable state'),
        JSON_OBJECT('id', 'god-object', 'label', 'Put unrelated behavior into one object')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('constructor-dependency', 'small-interface')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3107, 3007, 1, 'SINGLE_CHOICE',
     'Which Java collection is intended to keep unique values without duplicate elements?',
     2, 45, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'set', 'label', 'Set'),
        JSON_OBJECT('id', 'list', 'label', 'List'),
        JSON_OBJECT('id', 'queue', 'label', 'Queue'),
        JSON_OBJECT('id', 'string-builder', 'label', 'StringBuilder')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('set')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (3108, 3008, 1, 'MULTIPLE_CHOICE',
     'Which properties make a unit test dependable? Select all that apply.',
     2, 60, 'EXACT',
     JSON_ARRAY(
        JSON_OBJECT('id', 'deterministic', 'label', 'It is deterministic for the same inputs'),
        JSON_OBJECT('id', 'isolated', 'label', 'It isolates the behavior under test'),
        JSON_OBJECT('id', 'order-dependent', 'label', 'It depends on another test running first'),
        JSON_OBJECT('id', 'production-data', 'label', 'It requires production data')
     ), JSON_OBJECT('correctOptionIds', JSON_ARRAY('deterministic', 'isolated')), NULL,
     'ACTIVE', 'HUMAN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO question_knowledge (
    id, question_version_id, graph_version_id, knowledge_node_id, dimension, weight,
    max_evidence_strength, rubric_criterion_key, created_at
) VALUES
    (3201, 3101, 1, 1001, 'RECOGNITION', 1.0000, 0.4500, NULL, UTC_TIMESTAMP(6)),
    (3202, 3102, 1, 1003, 'UNDERSTANDING', 1.0000, 0.5500, NULL, UTC_TIMESTAMP(6)),
    (3203, 3103, 1, 1004, 'RECOGNITION', 1.0000, 0.4500, NULL, UTC_TIMESTAMP(6)),
    (3204, 3104, 1, 1005, 'UNDERSTANDING', 1.0000, 0.5500, NULL, UTC_TIMESTAMP(6)),
    (3205, 3105, 1, 1006, 'RECOGNITION', 1.0000, 0.4500, NULL, UTC_TIMESTAMP(6)),
    (3206, 3106, 1, 1007, 'UNDERSTANDING', 1.0000, 0.5500, NULL, UTC_TIMESTAMP(6)),
    (3207, 3107, 1, 1008, 'RECOGNITION', 1.0000, 0.4500, NULL, UTC_TIMESTAMP(6)),
    (3208, 3108, 1, 1010, 'UNDERSTANDING', 1.0000, 0.5500, NULL, UTC_TIMESTAMP(6));
