package com.blue333.ordermgmt.service;

import com.blue333.ordermgmt.config.S3Properties;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

  private final MinioClient internalMinioClient;
  private final MinioClient presignMinioClient;
  private final S3Properties properties;

  /** Convenience constructor for a single endpoint, retained for unit tests and non-container deployments. */
  public FileStorageService(MinioClient minioClient, S3Properties properties) {
    this(minioClient, minioClient, properties);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public FileStorageService(@org.springframework.beans.factory.annotation.Qualifier("s3InternalClient") MinioClient internalMinioClient,
      @org.springframework.beans.factory.annotation.Qualifier("s3PresignClient") MinioClient presignMinioClient,
      S3Properties properties) {
    this.internalMinioClient = internalMinioClient;
    this.presignMinioClient = presignMinioClient;
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
      return Optional.of(internalMinioClient.statObject(StatObjectArgs.builder()
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

  /**
   * Stores server-generated content (the A-19 archive PDF) through the internal endpoint. User
   * uploads keep using presigned PUT so bytes never pass through the application.
   */
  public void putObject(String objectKey, byte[] content, String contentType) {
    validateObjectKey(objectKey);
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Object content must not be empty");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("Content type is required");
    }
    try {
      internalMinioClient.putObject(PutObjectArgs.builder()
          .bucket(properties.bucket())
          .object(objectKey)
          .stream(new ByteArrayInputStream(content), content.length, -1)
          .contentType(contentType)
          .build());
    } catch (Exception exception) {
      throw new FileStorageException("Could not store object", exception);
    }
  }

  /**
   * Lists every object under a prefix. Used by the orphan sweep (P4-T11); the caller compares the
   * keys with the database before touching anything.
   */
  public List<StoredObject> listObjects(String prefix) {
    List<StoredObject> objects = new ArrayList<>();
    try {
      Iterable<io.minio.Result<Item>> results = internalMinioClient.listObjects(
          ListObjectsArgs.builder().bucket(properties.bucket()).prefix(prefix).recursive(true).build());
      for (io.minio.Result<Item> result : results) {
        Item item = result.get();
        objects.add(new StoredObject(item.objectName(), item.size(),
            item.lastModified() == null ? null
                : item.lastModified().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
      }
      return objects;
    } catch (Exception exception) {
      throw new FileStorageException("Could not list stored objects", exception);
    }
  }

  /** Server-side copy used to park an orphan under the quarantine prefix before deleting the original. */
  public void copyObject(String sourceKey, String targetKey) {
    validateObjectKey(sourceKey);
    validateObjectKey(targetKey);
    try {
      internalMinioClient.copyObject(CopyObjectArgs.builder()
          .bucket(properties.bucket())
          .object(targetKey)
          .source(CopySource.builder().bucket(properties.bucket()).object(sourceKey).build())
          .build());
    } catch (Exception exception) {
      throw new FileStorageException("Could not copy stored object", exception);
    }
  }

  public void removeObject(String objectKey) {
    validateObjectKey(objectKey);
    try {
      internalMinioClient.removeObject(RemoveObjectArgs.builder()
          .bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception exception) {
      throw new FileStorageException("Could not remove stored object", exception);
    }
  }

  /** Bucket listing row; {@code lastModified} is expressed in UTC to match the injected UTC clock. */
  public record StoredObject(String key, long size, LocalDateTime lastModified) {
  }

  private PresignedUrlResponse generatePresignedUrl(String objectKey, long expiryMinutes, Method method) {
    validateObjectKey(objectKey);
    validateExpiry(expiryMinutes);
    try {
      String url = presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
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
