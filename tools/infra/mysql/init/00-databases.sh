#!/bin/sh
set -eu

business_database="${MYSQL_DATABASE:-lab_sys}"
uid_database="${UID_DATABASE:-fun_cloud_base}"

validate_database_name() {
    case "$1" in
        ''|*[!A-Za-z0-9_]*)
            echo "invalid MySQL database name: $1" >&2
            exit 1
            ;;
    esac
}

validate_database_name "$business_database"
validate_database_name "$uid_database"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${business_database}\`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS \`${uid_database}\`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
