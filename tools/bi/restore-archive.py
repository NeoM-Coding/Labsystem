#!/usr/bin/env python3
"""Verify an archive downloaded with mc and emit SQL ONLY for an isolated restore table."""
import argparse
import gzip
import hashlib
import json
import re
from pathlib import Path

def verify(archive, manifest):
    meta = json.loads(manifest.read_text())
    if meta.get("schemaVersion") != 1:
        raise ValueError("Unsupported manifest version")
    with archive.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != meta["sha256"]:
            raise ValueError("Archive SHA-256 mismatch")
    tables = {"access_record", "air_condition_record", "circuit_break_record", "light_record", "sensor_record"}
    if meta["table"] not in tables or not re.fullmatch(r"[a-f0-9-]{36}", meta["batchId"]):
        raise ValueError("Invalid table or batch ID")
    with gzip.open(archive, "rt", encoding="utf-8") as source:
        rows = [json.loads(line) for line in source]
    if len(rows) != meta["rowCount"] or len(meta["records"]) != len(rows):
        raise ValueError("Record count mismatch")
    seen = set()
    for envelope, entry in zip(rows, meta["records"]):
        row = envelope["row"]
        digest = hashlib.sha256(json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        if envelope.get("schemaVersion") != 1 or envelope.get("table") != meta["table"] or row["id"] != entry["id"] or row["update_at"] != entry["version"] or digest != entry["sha256"] or row["id"] in seen:
            raise ValueError("Record identity, version or checksum mismatch")
        seen.add(row["id"])
    return meta, [envelope["row"] for envelope in rows]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="New SQL output path; existing files are never overwritten")
    args = parser.parse_args()
    meta, rows = verify(args.archive, args.manifest)
    target = "bi_restore_" + meta["batchId"].replace("-", "")
    def literal(value):
        return "NULL" if value is None else "CONVERT(0x" + str(value).encode().hex() + " USING utf8mb4)"
    statements = ["-- Verified archive; production record tables are never modified.",
                  "CREATE TABLE " + target + " LIKE " + meta["table"] + ";", "START TRANSACTION;"]
    for row in rows:
        if not all(re.fullmatch(r"[a-z_]+", key) for key in row):
            raise ValueError("Invalid column name")
        statements.append("INSERT INTO " + target + " (" + ",".join(row) + ") VALUES (" + ",".join(literal(value) for value in row.values()) + ");")
    statements.extend(["COMMIT;", "SELECT COUNT(*) AS restored_rows FROM " + target + ";"])
    with args.output.open("x", encoding="utf-8") as output:
        output.write("\n".join(statements) + "\n")
    print("Verified", len(rows), "rows; generated", args.output, "for", target)

if __name__ == "__main__":
    main()
