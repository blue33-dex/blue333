package com.blue333.ordermgmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Settings for the S3-compatible object store (SeaweedFS S3 Gateway in local deployments).
 *
 * <p>{@code endpoint} is used by the backend itself; {@code publicEndpoint} is embedded in the
 * presigned URLs handed to browsers, so it must be reachable from the user's machine.</p>
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
    String endpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String region) {
  public static final String DEFAULT_REGION = "us-east-1";

  // Two constructors exist, so Spring Boot must be told which one binds properties.
  @ConstructorBinding
  public S3Properties {
    if (publicEndpoint == null || publicEndpoint.isBlank()) {
      publicEndpoint = endpoint;
    }
    if (region == null || region.isBlank()) {
      region = DEFAULT_REGION;
    }
  }

  /** Single-endpoint constructor for tests and deployments where both addresses are the same. */
  public S3Properties(String endpoint, String accessKey, String secretKey, String bucket) {
    this(endpoint, endpoint, accessKey, secretKey, bucket, DEFAULT_REGION);
  }
}
