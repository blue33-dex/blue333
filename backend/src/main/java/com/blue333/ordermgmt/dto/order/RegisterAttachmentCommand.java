package com.blue333.ordermgmt.dto.order;

/** Metadata for a file already stored through the storage flow; this does not upload file bytes. */
public record RegisterAttachmentCommand(String fileName, long fileSize, String storagePath) {
}
