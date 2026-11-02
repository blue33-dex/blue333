package com.blue333.ordermgmt.dto.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A client-safe result for a direct object-storage operation.
 * Credentials and bucket configuration are intentionally not exposed.
 */
public record PresignedUrlResponse(
    @JsonProperty("presigned_url") String presignedUrl,
    @JsonProperty("object_key") String objectKey,
    @JsonProperty("expires_in_minutes") long expiresInMinutes) {
}
