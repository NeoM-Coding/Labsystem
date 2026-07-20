#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${LAB_ENV_FILE:-$ROOT_DIR/.env}"
ENV_EXAMPLE="$ROOT_DIR/.env.example"
COMPOSE_FILE="$ROOT_DIR/compose.yml"
COMMAND="${1:-deploy}"

log() {
    printf '[deploy] %s\n' "$*"
}

die() {
    printf '[deploy] ERROR: %s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: ./scripts/deploy.sh [deploy|bootstrap|status|logs|down]

  deploy     Start the Docker stack, wait for health checks, then initialize data.
  bootstrap  Reapply SQL schema, Permify DSL and the initial super administrator.
  status     Show Compose service status.
  logs       Follow Compose logs.
  down       Stop the Docker stack without deleting volumes.

Set LAB_ENV_FILE=/path/to/.env to use another environment file.
EOF
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

ensure_environment() {
    if [[ ! -f "$ENV_FILE" ]]; then
        cp "$ENV_EXAMPLE" "$ENV_FILE"
        log "created $ENV_FILE from .env.example"
        log "local default credentials are enabled; change them before non-local deployment"
    fi

    set -a
    # The environment file is maintained by the project owner and must remain shell-compatible.
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
}

require_variable() {
    [[ -n "${!1:-}" ]] || die "environment variable is required: $1"
}

validate_identifier() {
    [[ "$2" =~ ^[A-Za-z0-9._:-]+$ ]] || die "$1 contains unsupported characters: $2"
}

validate_database_name() {
    [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || die "invalid MySQL database name: $1"
}

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

mysql_query() {
    compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
        mysql --default-character-set=utf8mb4 --batch --skip-column-names \
        -uroot "$MYSQL_DATABASE"
}

mysql_root_query() {
    compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
        mysql --default-character-set=utf8mb4 --batch --skip-column-names -uroot
}

utf8_sql() {
    local hex
    hex="$(printf '%s' "$1" | LC_ALL=C od -An -v -tx1 | tr -d ' \n')"
    printf 'CONVERT(0x%s USING utf8mb4)' "$hex"
}

wait_for_mysql() {
    local deadline=$((SECONDS + DEPLOY_WAIT_TIMEOUT))
    until compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
            mysqladmin ping -uroot --silent >/dev/null 2>&1; do
        (( SECONDS < deadline )) || die "MySQL did not become ready in ${DEPLOY_WAIT_TIMEOUT}s"
        sleep 2
    done
}

wait_for_permify() {
    local deadline=$((SECONDS + DEPLOY_WAIT_TIMEOUT))
    local url="http://127.0.0.1:${PERMIFY_HTTP_PORT}/healthz"
    until curl --fail --silent --show-error "$url" >/dev/null 2>&1; do
        (( SECONDS < deadline )) || die "Permify did not become ready in ${DEPLOY_WAIT_TIMEOUT}s"
        sleep 2
    done
}

apply_database_schema() {
    log "applying MySQL schema"
    validate_database_name "$MYSQL_DATABASE"
    mysql_root_query <<SQL
CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
    mysql_query < "$ROOT_DIR/sql/schema.sql"
}

bootstrap_super_admin_user() {
    local id_sql name_sql username_sql password_sql email_sql mark_sql
    local id_owner name_owner

    id_sql="$(utf8_sql "$SUPER_ADMIN_USER_ID")"
    name_sql="$(utf8_sql "$SUPER_ADMIN_NAME")"
    username_sql="$(utf8_sql "$SUPER_ADMIN_USERNAME")"
    password_sql="$(utf8_sql "$SUPER_ADMIN_PASSWORD_BCRYPT")"
    email_sql="$(utf8_sql "$SUPER_ADMIN_EMAIL")"
    mark_sql="$(utf8_sql "$SUPER_ADMIN_MARK")"

    id_owner="$(mysql_query <<SQL
SELECT username FROM \`user\` WHERE id = ${id_sql} LIMIT 1;
SQL
)"
    name_owner="$(mysql_query <<SQL
SELECT username FROM \`user\` WHERE name = ${name_sql} LIMIT 1;
SQL
)"
    [[ -z "$id_owner" || "$id_owner" == "$SUPER_ADMIN_USERNAME" ]] \
        || die "SUPER_ADMIN_USER_ID is already used by another user: $id_owner"
    [[ -z "$name_owner" || "$name_owner" == "$SUPER_ADMIN_USERNAME" ]] \
        || die "SUPER_ADMIN_NAME is already used by another user: $name_owner"

    mysql_query <<SQL
INSERT INTO \`user\` (
    id, name, username, password, phone, email, mark, create_at, update_at, delete_at
) VALUES (
    ${id_sql}, ${name_sql}, ${username_sql}, ${password_sql}, NULL,
    ${email_sql}, ${mark_sql}, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), NULL
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    password = VALUES(password),
    email = VALUES(email),
    mark = VALUES(mark),
    update_at = CURRENT_TIMESTAMP(3),
    delete_at = NULL;
SQL

    BOOTSTRAP_USER_ID="$(mysql_query <<SQL
SELECT id FROM \`user\` WHERE username = ${username_sql} LIMIT 1;
SQL
)"
    [[ -n "$BOOTSTRAP_USER_ID" ]] || die "failed to create or update the super administrator"
    log "super administrator is ready: ${SUPER_ADMIN_USERNAME} (${BOOTSTRAP_USER_ID})"
}

schema_request_body() {
    awk '
        BEGIN { printf "{\"schema\":\"" }
        {
            gsub(/\\/, "\\\\")
            gsub(/\"/, "\\\"")
            gsub(/\t/, "\\t")
            gsub(/\r/, "\\r")
            if (NR > 1) printf "\\n"
            printf "%s", $0
        }
        END { print "\"}" }
    ' "$1"
}

schema_checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
        return
    fi
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
        return
    fi
    die "sha256sum or shasum is required to checksum the Permify schema"
}

store_bootstrap_metadata() {
    local key_sql value_sql
    key_sql="$(utf8_sql "$1")"
    value_sql="$(utf8_sql "$2")"
    mysql_query <<SQL
INSERT INTO \`system_bootstrap_metadata\` (meta_key, meta_value)
VALUES (${key_sql}, ${value_sql})
ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value), update_at = CURRENT_TIMESTAMP(3);
SQL
}

read_bootstrap_metadata() {
    local key_sql
    key_sql="$(utf8_sql "$1")"
    mysql_query <<SQL
SELECT meta_value FROM \`system_bootstrap_metadata\` WHERE meta_key = ${key_sql} LIMIT 1;
SQL
}

upload_permify_schema() {
    local schema_file schema_response schema_list
    local current_checksum stored_checksum stored_version

    schema_file="$PERMIFY_SCHEMA_FILE"
    [[ "$schema_file" = /* ]] || schema_file="$ROOT_DIR/$schema_file"
    [[ -f "$schema_file" ]] || die "Permify schema file not found: $schema_file"

    current_checksum="$(schema_checksum "$schema_file")"
    stored_checksum="$(read_bootstrap_metadata 'permify.schema.checksum')"
    stored_version="$(read_bootstrap_metadata 'permify.schema.version')"
    if [[ "$current_checksum" == "$stored_checksum" && -n "$stored_version" ]]; then
        schema_list="$(curl --fail-with-body --silent --show-error \
            -X POST "http://127.0.0.1:${PERMIFY_HTTP_PORT}/v1/tenants/${PERMIFY_TENANT_ID}/schemas/list" \
            -H 'Content-Type: application/json' \
            --data-binary '{"page_size":100,"continuous_token":""}')"
        if printf '%s' "$schema_list" | grep -Fq "\"$stored_version\""; then
            PERMIFY_SCHEMA_VERSION="$stored_version"
            log "Permify schema is unchanged: $PERMIFY_SCHEMA_VERSION"
            return
        fi
    fi

    log "uploading Permify schema: $schema_file"
    schema_response="$(curl --fail-with-body --silent --show-error \
        -X POST "http://127.0.0.1:${PERMIFY_HTTP_PORT}/v1/tenants/${PERMIFY_TENANT_ID}/schemas/write" \
        -H 'Content-Type: application/json' \
        --data-binary "$(schema_request_body "$schema_file")")"
    PERMIFY_SCHEMA_VERSION="$(printf '%s' "$schema_response" \
        | sed -nE 's/.*"schema_version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p')"
    [[ -n "$PERMIFY_SCHEMA_VERSION" ]] || die "Permify did not return schema_version: $schema_response"
    store_bootstrap_metadata 'permify.schema.checksum' "$current_checksum"
    store_bootstrap_metadata 'permify.schema.version' "$PERMIFY_SCHEMA_VERSION"
    log "Permify schema version: $PERMIFY_SCHEMA_VERSION"
}

grant_permify_super_admin() {
    local read_response read_body write_body

    read_body="$(printf \
        '{"filter":{"entity":{"type":"app","ids":["%s"]},"relation":"super_admin","subject":{"type":"user","ids":["%s"]}},"page_size":1,"continuous_token":""}' \
        "$PERMIFY_APP_ID" "$BOOTSTRAP_USER_ID")"
    read_response="$(curl --fail-with-body --silent --show-error \
        -X POST "http://127.0.0.1:${PERMIFY_HTTP_PORT}/v1/tenants/${PERMIFY_TENANT_ID}/data/relationships/read" \
        -H 'Content-Type: application/json' --data-binary "$read_body")"

    if printf '%s' "$read_response" | grep -Eq '"relation"[[:space:]]*:[[:space:]]*"super_admin"'; then
        log "Permify super_admin relation already exists"
        return
    fi

    write_body="$(printf \
        '{"metadata":{"schema_version":"%s"},"tuples":[{"entity":{"type":"app","id":"%s"},"relation":"super_admin","subject":{"type":"user","id":"%s"}}]}' \
        "$PERMIFY_SCHEMA_VERSION" "$PERMIFY_APP_ID" "$BOOTSTRAP_USER_ID")"
    curl --fail-with-body --silent --show-error \
        -X POST "http://127.0.0.1:${PERMIFY_HTTP_PORT}/v1/tenants/${PERMIFY_TENANT_ID}/relationships/write" \
        -H 'Content-Type: application/json' --data-binary "$write_body" >/dev/null
    log "granted app:${PERMIFY_APP_ID}#super_admin@user:${BOOTSTRAP_USER_ID}"
}

bootstrap() {
    require_command curl
    require_command od
    require_variable MYSQL_DATABASE
    require_variable MYSQL_PASSWORD
    require_variable PERMIFY_HTTP_PORT
    require_variable PERMIFY_TENANT_ID
    require_variable PERMIFY_APP_ID
    require_variable PERMIFY_SCHEMA_FILE
    require_variable SUPER_ADMIN_USER_ID
    require_variable SUPER_ADMIN_NAME
    require_variable SUPER_ADMIN_USERNAME
    require_variable SUPER_ADMIN_PASSWORD_BCRYPT
    require_variable SUPER_ADMIN_EMAIL
    require_variable SUPER_ADMIN_MARK
    [[ "$PERMIFY_TENANT_ID" =~ ^[A-Za-z0-9,-]+$ ]] \
        || die "PERMIFY_TENANT_ID contains unsupported characters: $PERMIFY_TENANT_ID"
    validate_identifier PERMIFY_APP_ID "$PERMIFY_APP_ID"
    validate_identifier SUPER_ADMIN_USER_ID "$SUPER_ADMIN_USER_ID"
    [[ "$SUPER_ADMIN_PASSWORD_BCRYPT" == '$2a$'* ]] \
        || die "SUPER_ADMIN_PASSWORD_BCRYPT must be a precompiled BCrypt 2a hash"

    wait_for_mysql
    wait_for_permify
    apply_database_schema
    bootstrap_super_admin_user
    upload_permify_schema
    grant_permify_super_admin
}

case "$COMMAND" in
    -h|--help|help)
        usage
        exit 0
        ;;
    deploy|bootstrap|status|logs|down)
        ;;
    *)
        usage >&2
        die "unknown command: $COMMAND"
        ;;
esac

require_command docker
ensure_environment
DEPLOY_WAIT_TIMEOUT="${DEPLOY_WAIT_TIMEOUT:-180}"
[[ "$DEPLOY_WAIT_TIMEOUT" =~ ^[0-9]+$ ]] || die "DEPLOY_WAIT_TIMEOUT must be an integer"

case "$COMMAND" in
    deploy)
        log_path="${LOG_PATH:-logs}"
        [[ "$log_path" = /* ]] || log_path="$ROOT_DIR/${log_path#./}"
        mkdir -p "$log_path"
        compose config --quiet
        log "starting Docker services"
        compose up -d --wait --wait-timeout "$DEPLOY_WAIT_TIMEOUT"
        bootstrap
        compose ps
        log "deployment and bootstrap completed"
        ;;
    bootstrap)
        bootstrap
        ;;
    status)
        compose ps
        ;;
    logs)
        compose logs -f
        ;;
    down)
        compose down
        ;;
esac
