package com.blue333.ordermgmt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blue333.ordermgmt.config.S3Properties;
import com.blue333.ordermgmt.dto.storage.PresignedUrlResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FileStorageServiceTest {
  private MinioClient minioClient;
  private FileStorageService fileStorageService;

  @BeforeEach
  void setUp() {
    minioClient = Mockito.mock(MinioClient.class);
    fileStorageService = new FileStorageService(
        minioClient, new S3Properties("http://s3:8333", "access", "secret", "ordermgmt"));
  }

  @Test
  void generatesPutUrlUsingConfiguredBucketAndRequestedExpiry() throws Exception {
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://minio:9000/ordermgmt/attachments/one.pdf?signature=value");

    PresignedUrlResponse response = fileStorageService.generatePresignedPutUrl("attachments/one.pdf", 15);

    assertThat(response.presignedUrl()).contains("signature=value");
    assertThat(response.objectKey()).isEqualTo("attachments/one.pdf");
    assertThat(response.expiresInMinutes()).isEqualTo(15);
    ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
    verify(minioClient).getPresignedObjectUrl(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo("ordermgmt");
    assertThat(captor.getValue().object()).isEqualTo("attachments/one.pdf");
    assertThat(captor.getValue().method().name()).isEqualTo("PUT");
  }

  @Test
  void generatesGetUrlUsingDefaultExpiry() throws Exception {
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("http://minio:9000/ordermgmt/attachments/one.pdf?signature=value");

    PresignedUrlResponse response = fileStorageService.generatePresignedGetUrl("attachments/one.pdf");

    assertThat(response.expiresInMinutes()).isEqualTo(FileStorageService.DEFAULT_GET_EXPIRY_MINUTES);
    ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
    verify(minioClient).getPresignedObjectUrl(captor.capture());
    assertThat(captor.getValue().method().name()).isEqualTo("GET");
  }

  @Test
  void rejectsUnsafeObjectKeysBeforeCallingStorage() {
    assertThatIllegalArgumentException().isThrownBy(
        () -> fileStorageService.generatePresignedPutUrl("../private.pdf", 15));
    assertThatIllegalArgumentException().isThrownBy(
        () -> fileStorageService.generatePresignedPutUrl("attachments/a\nfile.pdf", 15));
    assertThatIllegalArgumentException().isThrownBy(
        () -> fileStorageService.generatePresignedPutUrl("/bucket/object.pdf", 15));
  }

  @Test
  void generatesKeyWithoutRetainingUntrustedFileName() {
    String objectKey = fileStorageService.generateObjectKey("attachments/order-12", "Source File.PDF");

    assertThat(objectKey).matches("attachments/order-12/[0-9a-f-]{36}\\.pdf");
  }

  @Test
  void storesServerGeneratedContentThroughTheInternalEndpoint() throws Exception {
    fileStorageService.putObject("archives/order-1/report.pdf", new byte[] {1, 2, 3}, "application/pdf");

    ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
    verify(minioClient).putObject(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo("ordermgmt");
    assertThat(captor.getValue().object()).isEqualTo("archives/order-1/report.pdf");
    assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
  }

  @Test
  void rejectsEmptyServerGeneratedContent() {
    assertThatIllegalArgumentException().isThrownBy(
        () -> fileStorageService.putObject("archives/order-1/report.pdf", new byte[0], "application/pdf"));
    assertThatIllegalArgumentException().isThrownBy(
        () -> fileStorageService.putObject("archives/order-1/report.pdf", new byte[] {1}, " "));
  }
}
