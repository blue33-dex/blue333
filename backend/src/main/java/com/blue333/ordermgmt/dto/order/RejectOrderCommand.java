package com.blue333.ordermgmt.dto.order;

import com.blue333.ordermgmt.domain.enums.RejectReasonType;

/** Rejection information required before an order can be returned for correction. */
public record RejectOrderCommand(Long orderId, RejectReasonType reasonType, String remark) {
}
