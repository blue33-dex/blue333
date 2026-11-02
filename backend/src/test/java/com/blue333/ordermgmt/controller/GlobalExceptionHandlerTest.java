package com.blue333.ordermgmt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blue333.ordermgmt.service.AuthService;
import com.blue333.ordermgmt.service.InvalidStateTransitionException;
import com.blue333.ordermgmt.service.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Standalone MockMvc (no application context) covering the error mapping and AuthController behavior. */
class GlobalExceptionHandlerTest {
  private AuthService authService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService), new ThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void authControllerKeepsItsStatusCodes() throws Exception {
    when(authService.login(any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

    mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"a\",\"password\":\"b\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Invalid username or password"));

    mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void mapsDomainExceptionsToStatusAndCode() throws Exception {
    expect("/test/illegal-argument", 400, "VALIDATION_ERROR");
    expect("/test/not-found", 404, "NOT_FOUND");
    expect("/test/invalid-transition", 409, "INVALID_STATE_TRANSITION");
    expect("/test/illegal-state", 422, "PRECONDITION_FAILED");
    expect("/test/forbidden", 403, "FORBIDDEN");
    expect("/test/teapot", 418, "I_AM_A_TEAPOT");
    expect("/test/typed/not-a-number", 400, "VALIDATION_ERROR");
  }

  @Test
  void unexpectedErrorsHideInternalDetails() throws Exception {
    mockMvc.perform(get("/test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
        .andExpect(jsonPath("$.detail").doesNotExist())
        .andExpect(content().string(org.hamcrest.Matchers.not(
            org.hamcrest.Matchers.containsString("secret-db-host"))));
  }

  private void expect(String path, int status, String code) throws Exception {
    mockMvc.perform(get(path))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.detail").doesNotExist());
  }

  @RestController
  static class ThrowingController {
    @GetMapping("/test/illegal-argument")
    void illegalArgument() {
      throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/test/not-found")
    void notFound() {
      throw new ResourceNotFoundException("Order does not exist: 1");
    }

    @GetMapping("/test/invalid-transition")
    void invalidTransition() {
      throw new InvalidStateTransitionException("no");
    }

    @GetMapping("/test/illegal-state")
    void illegalState() {
      throw new IllegalStateException("attachment required");
    }

    @GetMapping("/test/forbidden")
    void forbidden() {
      throw new AccessDeniedException("nope");
    }

    @GetMapping("/test/teapot")
    void teapot() {
      throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT);
    }

    @GetMapping("/test/typed/{id}")
    void typed(@PathVariable("id") Long id) {
    }

    @GetMapping("/test/boom")
    void boom() {
      throw new RuntimeException("connection to secret-db-host failed");
    }
  }
}
