package com.blue333.ordermgmt.dto.order;

import com.blue333.ordermgmt.domain.enums.MaterialType;

/** Material requirement supplied while creating an order. */
public record MaterialSpecCommand(MaterialType materialType, String specSize) {
}
