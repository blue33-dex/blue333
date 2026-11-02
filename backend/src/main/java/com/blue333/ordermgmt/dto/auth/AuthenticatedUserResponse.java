package com.blue333.ordermgmt.dto.auth;

import java.util.List;

public record AuthenticatedUserResponse(Long id, String username, String name, List<String> roles) {
}
