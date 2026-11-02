package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
