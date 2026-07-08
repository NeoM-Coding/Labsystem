CREATE TABLE rule_runtime (
    id VARCHAR(64) PRIMARY KEY,
    runtime_id VARCHAR(64) NOT NULL UNIQUE,
    runtime_name VARCHAR(128) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    published_revision_no INT,
    active_from TIMESTAMP(3),
    active_until TIMESTAMP(3),
    create_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    delete_at TIMESTAMP(3)
);

CREATE TABLE rule_runtime_revision (
    id VARCHAR(64) PRIMARY KEY,
    runtime_id VARCHAR(64) NOT NULL,
    revision_no INT NOT NULL,
    schema_version INT NOT NULL,
    definition CLOB NOT NULL,
    checksum CHAR(64),
    created_by VARCHAR(64) NOT NULL,
    create_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (runtime_id, revision_no),
    FOREIGN KEY (runtime_id) REFERENCES rule_runtime(runtime_id)
);
