#!/bin/sh
set -eu

MINIO_ALIAS="local"
BUCKET_NAME="ordermgmt"
POLICY_NAME="ordermgmt-object-rw"

mc alias set "$MINIO_ALIAS" "http://minio:9000" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

until mc ready "$MINIO_ALIAS" >/dev/null 2>&1; do
  echo "Waiting for MinIO to become ready..."
  sleep 2
done

mc mb --ignore-existing "$MINIO_ALIAS/$BUCKET_NAME"
mc version enable "$MINIO_ALIAS/$BUCKET_NAME"
mc anonymous set none "$MINIO_ALIAS/$BUCKET_NAME"

if mc admin policy info "$MINIO_ALIAS" "$POLICY_NAME" >/dev/null 2>&1; then
  mc admin policy remove "$MINIO_ALIAS" "$POLICY_NAME"
fi
mc admin policy create "$MINIO_ALIAS" "$POLICY_NAME" /policies/ordermgmt-object-rw.json

if ! mc admin user info "$MINIO_ALIAS" "$MINIO_APP_ACCESS_KEY" >/dev/null 2>&1; then
  mc admin user add "$MINIO_ALIAS" "$MINIO_APP_ACCESS_KEY" "$MINIO_APP_SECRET_KEY"
fi
mc admin policy attach "$MINIO_ALIAS" "$POLICY_NAME" --user "$MINIO_APP_ACCESS_KEY"

echo "MinIO bucket '$BUCKET_NAME' is private, versioned, and ready for presigned URL access."
