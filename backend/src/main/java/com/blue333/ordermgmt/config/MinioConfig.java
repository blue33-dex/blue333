package com.blue333.ordermgmt.config;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
  @Bean("minioInternalClient")
  MinioClient minioInternalClient(MinioProperties properties) {
    validateProperties(properties);
    return client(properties.endpoint(), properties);
  }

  @Bean("minioPresignClient")
  MinioClient minioPresignClient(MinioProperties properties) {
    validateProperties(properties);
    return client(properties.publicEndpoint(), properties);
  }

  private MinioClient client(String endpoint, MinioProperties properties) {
    return MinioClient.builder().endpoint(endpoint)
        .credentials(properties.accessKey(), properties.secretKey()).build();
  }

  private void validateProperties(MinioProperties properties) {
    if (isBlank(properties.endpoint()) || isBlank(properties.publicEndpoint()) || isBlank(properties.accessKey())
        || isBlank(properties.secretKey()) || isBlank(properties.bucket())) {
      throw new IllegalStateException("MinIO endpoint, credentials, and bucket must be configured");
    }
    validateEndpoint(properties.endpoint());
    validateEndpoint(properties.publicEndpoint());
  }

  private void validateEndpoint(String rawEndpoint) {
    URI endpoint = URI.create(rawEndpoint);
    if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())
        || isBlank(endpoint.getHost())) {
      throw new IllegalStateException("MinIO endpoint must be an HTTP(S) URL with a host");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
