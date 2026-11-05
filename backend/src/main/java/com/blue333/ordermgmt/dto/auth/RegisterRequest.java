package com.blue333.ordermgmt.dto.auth;

/** 三端注册请求，{@code role} 取 AM / BO / DS。 */
public record RegisterRequest(String name, String username, String password, String role) {
}
