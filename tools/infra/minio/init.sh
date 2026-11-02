#!/bin/sh
set -eu
mc alias set lab "${MINIO_INIT_ENDPOINT:-http://minio:9000}" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "lab/$MINIO_ARCHIVE_BUCKET"
mc mb --ignore-existing "lab/$MINIO_DEVICE_BUCKET"
mc anonymous set none "lab/$MINIO_ARCHIVE_BUCKET"
mc anonymous set none "lab/$MINIO_DEVICE_BUCKET"
cat > /tmp/lab-storage-policy.json <<EOF
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket","s3:ListBucketMultipartUploads"],"Resource":["arn:aws:s3:::$MINIO_ARCHIVE_BUCKET","arn:aws:s3:::$MINIO_DEVICE_BUCKET"]},
 {"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:AbortMultipartUpload","s3:ListMultipartUploadParts"],"Resource":["arn:aws:s3:::$MINIO_ARCHIVE_BUCKET/*","arn:aws:s3:::$MINIO_DEVICE_BUCKET/*"]}
]}
EOF
mc admin policy create lab lab-storage /tmp/lab-storage-policy.json
mc admin user add lab "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
mc admin policy attach lab lab-storage --user "$MINIO_ACCESS_KEY"
