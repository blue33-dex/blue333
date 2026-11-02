package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.config.MinioProperties;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {
  public static final long DEFAULT_PUT_EXPIRY_MINUTES = 15;
  public static final long DEFAULT_GET_EXPIRY_MINUTES = 5;
  private static final long MAX_PRESIGNED_URL_EXPIRY_MINUTES = 7L * 24 * 60;

  private final MinioClient minioClient;
  private final MinioProperties properties;

  public FileStorageService(MinioClient minioClient, MinioProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  public String generateObjectKey(String prefix, String originalFileName) {
    validateObjectKey(prefix);
    validateFileName(originalFileName);
    String extension = extensionOf(originalFileName);
    return prefix + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
  }

  public PresignedUrlResponse generatePresignedPutUrl(String objectKey) {
    return generatePresignedPutUrl(objectKey, DEFAULT_PUT_EXPIRY_MINUTES);
  }

  public PresignedUrlResponse generatePresignedPutUrl(String objectKey, long expiryMinutes) {
    return generatePresignedUrl(objectKey, expiryMinutes, Method.PUT);
  }

  public PresignedUrlResponse generatePresignedGetUrl(String objectKey) {
    return generatePresignedGetUrl(objectKey, DEFAULT_GET_EXPIRY_MINUTES);
  }

  public PresignedUrlResponse generatePresignedGetUrl(String objectKey, long expiryMinutes) {
    return generatePresignedUrl(objectKey, expiryMinutes, Method.GET);
  }

  public Optional<Long> findObjectSize(String objectKey) {
    validateObjectKey(objectKey);
    try {
      return Optional.of(minioClient.statObject(StatObjectArgs.builder()
          .bucket(properties.bucket()).object(objectKey).build()).size());
    } catch (ErrorResponseException exception) {
      String code = exception.errorResponse().code();
      if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
        return Optional.empty();
      }
      throw new FileStorageException("Could not inspect stored object", exception);
    } catch (Exception exception) {
      throw new FileStorageException("Could not inspect stored object", exception);
    }
  }

  private PresignedUrlResponse generatePresignedUrl(String objectKey, long expiryMinutes, Method method) {
    validateObjectKey(objectKey);
    validateExpiry(expiryMinutes);
    try {
      String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
          .method(method)
          .bucket(properties.bucket())
          .object(objectKey)
          .expiry(Math.toIntExact(expiryMinutes), TimeUnit.MINUTES)
          .build());
      return new PresignedUrlResponse(url, objectKey, expiryMinutes);
    } catch (Exception exception) {
      throw new FileStorageException("Could not generate a presigned storage URL", exception);
    }
  }

  private void validateObjectKey(String objectKey) {
    if (objectKey == null || objectKey.isBlank() || objectKey.length() > 512
        || objectKey.startsWith("/") || objectKey.endsWith("/") || objectKey.contains("\\")
        || objectKey.indexOf('?') >= 0 || objectKey.indexOf('#') >= 0) {
      throw new IllegalArgumentException("Object key must be a valid bucket-relative key");
    }
    for (String segment : objectKey.split("/", -1)) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || containsControlCharacter(segment)) {
        throw new IllegalArgumentException("Object key must be a valid bucket-relative key");
      }
    }
  }

  private void validateExpiry(long expiryMinutes) {
    if (expiryMinutes <= 0 || expiryMinutes > MAX_PRESIGNED_URL_EXPIRY_MINUTES) {
      throw new IllegalArgumentException("Presigned URL expiry must be between 1 and 10080 minutes");
    }
  }

  private void validateFileName(String fileName) {
    if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.indexOf('/') >= 0
        || fileName.indexOf('\\') >= 0 || containsControlCharacter(fileName)) {
      throw new IllegalArgumentException("File name must be a single valid file name");
    }
  }

  private boolean containsControlCharacter(String value) {
    return value.codePoints().anyMatch(Character::isISOControl);
  }

  private String extensionOf(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index < 0 || index == fileName.length() - 1
        ? ""
        : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
  }
}
