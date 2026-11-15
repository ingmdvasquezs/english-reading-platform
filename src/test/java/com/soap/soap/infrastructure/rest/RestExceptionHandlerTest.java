package com.soap.soap.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(OutputCaptureExtension.class)
class RestExceptionHandlerTest {
  private final RestExceptionHandler handler = new RestExceptionHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void unexpectedErrorIsLoggedWithStackTraceButReturnsASafeBody(CapturedOutput output) {
    MDC.put("correlationId", "req-500");
    var failure =
        new IllegalStateException(
            "jdbc:postgresql://db.internal/app password=s3cr3t at C:\\internal\\path");

    var response = handler.unexpected(failure);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.message()).isEqualTo("The request could not be completed.");
    assertThat(body.requestId()).isEqualTo("req-500");
    assertThat(body.toString())
        .doesNotContain("IllegalStateException", "jdbc", "s3cr3t", "internal", "at ");

    assertThat(output)
        .contains("rest.unexpected_error")
        .contains("java.lang.IllegalStateException")
        .contains("RestExceptionHandlerTest.unexpectedErrorIsLoggedWithStackTrace");
  }

  @Test
  void frameworkClientErrorKeepsItsStatusAndIsNotLoggedAsAnInternalError(CapturedOutput output)
      throws Exception {
    var response =
        handler.unexpected(new NoResourceFoundException(HttpMethod.GET, "/api/v1/x", "x"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    assertThat(output).doesNotContain("rest.unexpected_error");
  }

  @Test
  void explicitValidationIsABadRequestWithoutStackTrace(CapturedOutput output) {
    var response = handler.badRequest(new InvalidApplicationArgumentException("bad input"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getBody().message()).isEqualTo("The request is invalid.");
    assertThat(output).doesNotContain("bad input");
  }

  @Test
  void unclassifiedIllegalArgumentStaysBadRequestButIsLogged(CapturedOutput output) {
    var response = handler.unclassifiedIllegalArgument(new IllegalArgumentException("invariant"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("The request is invalid.");
    assertThat(output)
        .contains("rest.bad_request category=unclassified_illegal_argument")
        .contains("java.lang.IllegalArgumentException: invariant");
  }
}
