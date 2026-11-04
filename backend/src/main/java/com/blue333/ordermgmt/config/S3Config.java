package com.blue333.ordermgmt.config;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {
  @Bean("s3InternalClient")
  MinioClient s3InternalClient(S3Properties properties) {
    validateProperties(properties);
    return client(properties.endpoint(), properties);
  }

  @Bean("s3PresignClient")
  MinioClient s3PresignClient(S3Properties properties) {
    validateProperties(properties);
    return client(properties.publicEndpoint(), properties);
  }

  /**
   * A fixed region stops the client from calling GetBucketLocation before signing. That lookup
   * would fail for the presign client, whose public endpoint is not reachable from the container.
   */
  private MinioClient client(String endpoint, S3Properties properties) {
    return MinioClient.builder().endpoint(endpoint)
        .region(properties.region())
        .credentials(properties.accessKey(), properties.secretKey()).build();
  }

  private void validateProperties(S3Properties properties) {
    if (isBlank(properties.endpoint()) || isBlank(properties.publicEndpoint()) || isBlank(properties.accessKey())
        || isBlank(properties.secretKey()) || isBlank(properties.bucket())) {
      throw new IllegalStateException("S3 endpoint, credentials, and bucket must be configured");
    }
    validateEndpoint(properties.endpoint());
    validateEndpoint(properties.publicEndpoint());
  }

  private void validateEndpoint(String rawEndpoint) {
    URI endpoint = URI.create(rawEndpoint);
    if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())
        || isBlank(endpoint.getHost())) {
      throw new IllegalStateException("S3 endpoint must be an HTTP(S) URL with a host");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
