#!/bin/sh
set -eu

: "${S3_ACCESS_KEY:?S3_ACCESS_KEY must be set}"
: "${S3_SECRET_KEY:?S3_SECRET_KEY must be set}"
: "${S3_ADMIN_ACCESS_KEY:?S3_ADMIN_ACCESS_KEY must be set}"
: "${S3_ADMIN_SECRET_KEY:?S3_ADMIN_SECRET_KEY must be set}"

if [ "$S3_ACCESS_KEY" = "$S3_ADMIN_ACCESS_KEY" ]; then
  echo "S3_ACCESS_KEY and S3_ADMIN_ACCESS_KEY must differ." >&2
  exit 1
fi

# Two identities:
#   ordermgmt-admin   - used only by the one-shot init container to create the bucket and set CORS.
#   ordermgmt-backend - used by Spring Boot; object read/write only, no bucket administration.
# No anonymous identity is defined, so unsigned requests are rejected.
mkdir -p /config
cat > /config/s3.json <<EOF
{
  "identities": [
    {
      "name": "ordermgmt-admin",
      "credentials": [
        { "accessKey": "${S3_ADMIN_ACCESS_KEY}", "secretKey": "${S3_ADMIN_SECRET_KEY}" }
      ],
      "actions": ["Admin", "Read", "List", "Write", "Tagging"]
    },
    {
      "name": "ordermgmt-backend",
      "credentials": [
        { "accessKey": "${S3_ACCESS_KEY}", "secretKey": "${S3_SECRET_KEY}" }
      ],
      "actions": ["Read", "List", "Write", "Tagging"]
    }
  ]
}
EOF

chmod 644 /config/s3.json
echo "SeaweedFS S3 identities written (admin for init, least-privilege identity for the backend)."
