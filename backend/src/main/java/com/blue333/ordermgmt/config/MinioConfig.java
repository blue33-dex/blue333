package com.blue333.ordermgmt.config;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
  @Bean
  MinioClient minioClient(MinioProperties properties) {
    validateProperties(properties);
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  private void validateProperties(MinioProperties properties) {
    if (isBlank(properties.endpoint()) || isBlank(properties.accessKey())
        || isBlank(properties.secretKey()) || isBlank(properties.bucket())) {
      throw new IllegalStateException("MinIO endpoint, credentials, and bucket must be configured");
    }
    URI endpoint = URI.create(properties.endpoint());
    if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())
        || isBlank(endpoint.getHost())) {
      throw new IllegalStateException("MinIO endpoint must be an HTTP(S) URL with a host");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
