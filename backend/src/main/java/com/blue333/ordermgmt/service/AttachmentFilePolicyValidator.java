package com.blue333.ordermgmt.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AttachmentFilePolicyValidator {
  public static final long MAX_FILE_SIZE_BYTES = 200L * 1024 * 1024;

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      "psd", "ai", "sketch", "fig", "png", "jpg", "jpeg", "gif", "svg", "pdf",
      "zip", "rar", "doc", "docx", "xls", "xlsx", "ppt", "pptx");

  public void validate(String fileName, long fileSizeBytes) {
    if (fileSizeBytes < 0) {
      throw new IllegalArgumentException("File size must not be negative");
    }
    if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
      throw new IllegalArgumentException("File size must not exceed 200 MB");
    }
    if (fileName == null || fileName.isBlank() || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
        || fileName.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("File name must be a single valid file name");
    }

    String extension = extensionOf(fileName);
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException("File extension is not allowed");
    }
  }

  public String extensionOf(String fileName) {
    int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
    int extensionStart = fileName.lastIndexOf('.');
    if (extensionStart <= separator || extensionStart == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
  }
}
