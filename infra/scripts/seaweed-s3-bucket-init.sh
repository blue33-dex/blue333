#!/bin/sh
set -eu

: "${S3_ADMIN_ACCESS_KEY:?S3_ADMIN_ACCESS_KEY must be set}"
: "${S3_ADMIN_SECRET_KEY:?S3_ADMIN_SECRET_KEY must be set}"
: "${S3_BUCKET:?S3_BUCKET must be set}"

# Bucket creation needs the admin identity; the backend identity cannot create buckets.
export AWS_ACCESS_KEY_ID="$S3_ADMIN_ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$S3_ADMIN_SECRET_KEY"
export AWS_DEFAULT_REGION="us-east-1"
ENDPOINT="http://seaweedfs:8333"

attempt=0
until aws --endpoint-url "$ENDPOINT" s3api list-buckets >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "SeaweedFS S3 Gateway did not become ready within 120 seconds." >&2
    exit 1
  fi
  echo "Waiting for SeaweedFS S3 Gateway..."
  sleep 2
done

if ! aws --endpoint-url "$ENDPOINT" s3api head-bucket --bucket "$S3_BUCKET" >/dev/null 2>&1; then
  aws --endpoint-url "$ENDPOINT" s3api create-bucket --bucket "$S3_BUCKET" >/dev/null
fi

# SeaweedFS 3.94 does not implement PutBucketCors. Browser CORS for presigned
# uploads/downloads is configured on the gateway with -s3.allowedOrigins
# (see the seaweedfs service command in docker-compose.yml).
echo "SeaweedFS S3 bucket '$S3_BUCKET' is ready (private, presigned access only)."
