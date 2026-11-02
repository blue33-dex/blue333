package com.blue333.ordermgmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.minio")
public record MinioProperties(
    String endpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket) {
  /** Backward-compatible constructor for internal-only tests and deployments. */
  public MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
    this(endpoint, endpoint, accessKey, secretKey, bucket);
  }
}
