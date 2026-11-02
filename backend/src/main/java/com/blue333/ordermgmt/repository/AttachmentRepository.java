package com.blue333.ordermgmt.repository;

import com.blue333.ordermgmt.domain.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
  boolean existsByOrderId(Long orderId);

  long countByOrderId(Long orderId);

  List<Attachment> findAllByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
