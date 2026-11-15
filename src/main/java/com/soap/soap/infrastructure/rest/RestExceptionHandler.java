package com.soap.soap.infrastructure.rest;

import com.soap.soap.application.exception.DocumentAlreadyImportedException;
import com.soap.soap.application.exception.DocumentImportException;
import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentNotReadyException;
import com.soap.soap.application.exception.DocumentProgressConflictException;
import com.soap.soap.application.exception.DocumentStillProcessingException;
import com.soap.soap.application.exception.DocumentUnitNotFoundException;
import com.soap.soap.application.exception.ImportCapacityExceededException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.soap.soap.infrastructure.rest")
public class RestExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(DocumentAlreadyImportedException.class)
  ResponseEntity<ApiError> duplicate(DocumentAlreadyImportedException exception) {
    return error(
        HttpStatus.CONFLICT,
        "DOCUMENT_ALREADY_IMPORTED",
        "This document has already been imported or is currently being processed.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.UploadNotCompletedException.class)
  ResponseEntity<ApiError> uploadNotCompleted(
      com.soap.soap.application.exception.UploadNotCompletedException exception) {
    return error(
        HttpStatus.CONFLICT,
        "UPLOAD_NOT_COMPLETED",
        "The object has not been uploaded to storage yet.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.UploadIntegrityMismatchException.class)
  ResponseEntity<ApiError> integrityMismatch(
      com.soap.soap.application.exception.UploadIntegrityMismatchException exception) {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY, "UPLOAD_INTEGRITY_MISMATCH", exception.getMessage());
  }

  @ExceptionHandler(com.soap.soap.application.exception.TransientStorageException.class)
  ResponseEntity<ApiError> transientStorage(
      com.soap.soap.application.exception.TransientStorageException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STORAGE_TRANSIENT_ERROR",
        "Storage service is temporarily unavailable. Please retry.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.StorageChecksumUnavailableException.class)
  ResponseEntity<ApiError> checksumUnavailable(
      com.soap.soap.application.exception.StorageChecksumUnavailableException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STORAGE_CHECKSUM_UNAVAILABLE",
        "Storage checksum is temporarily unavailable. Please retry confirmation.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.UploadNotFoundException.class)
  ResponseEntity<ApiError> uploadNotFound(
      com.soap.soap.application.exception.UploadNotFoundException exception) {
    return error(
        HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "The requested upload intent was not found.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.UploadExpiredException.class)
  ResponseEntity<ApiError> uploadExpired(
      com.soap.soap.application.exception.UploadExpiredException exception) {
    return error(HttpStatus.BAD_REQUEST, "UPLOAD_EXPIRED", "The upload intent has expired.");
  }

  @ExceptionHandler(com.soap.soap.application.exception.UploadAbortedException.class)
  ResponseEntity<ApiError> uploadAborted(
      com.soap.soap.application.exception.UploadAbortedException exception) {
    return error(
        HttpStatus.CONFLICT,
        "UPLOAD_ABORTED",
        "The upload intent was aborted due to integrity mismatch.");
  }

  @ExceptionHandler({DocumentNotFoundException.class, DocumentUnitNotFoundException.class})
  ResponseEntity<ApiError> notFound(RuntimeException exception) {
    return error(
        HttpStatus.NOT_FOUND,
        "DOCUMENT_NOT_FOUND",
        "The requested document resource was not found.");
  }

  @ExceptionHandler(DocumentNotReadyException.class)
  ResponseEntity<ApiError> notReady(DocumentNotReadyException exception) {
    return error(HttpStatus.CONFLICT, "DOCUMENT_NOT_READY", "The document is not ready.");
  }

  @ExceptionHandler(DocumentStillProcessingException.class)
  ResponseEntity<ApiError> processing(DocumentStillProcessingException exception) {
    return error(
        HttpStatus.CONFLICT,
        "DOCUMENT_PROCESSING",
        "Wait for the document import to finish before deleting it.");
  }

  @ExceptionHandler({
    DocumentProgressConflictException.class,
    org.springframework.orm.ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ApiError> conflict(RuntimeException exception) {
    return error(
        HttpStatus.CONFLICT,
        "PROGRESS_CONFLICT",
        "Document progress was updated concurrently. Refresh and retry.");
  }

  @ExceptionHandler(DocumentImportException.class)
  ResponseEntity<ApiError> importFailure(DocumentImportException exception) {
    return error(
        HttpStatus.UNPROCESSABLE_ENTITY, exception.reason().name(), message(exception.reason()));
  }

  @ExceptionHandler(ImportCapacityExceededException.class)
  ResponseEntity<ApiError> capacity(ImportCapacityExceededException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "IMPORT_CAPACITY_EXCEEDED",
        "Document import capacity is temporarily exhausted.");
  }

  /**
   * Explicit client errors: application validation, bean validation, missing parameters, malformed
   * or unreadable JSON bodies and non-convertible path/query values (for example a malformed UUID).
   * Parser details are never echoed to the client.
   */
  @ExceptionHandler({
    InvalidApplicationArgumentException.class,
    MethodArgumentNotValidException.class,
    MissingServletRequestPartException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> badRequest(Exception exception) {
    LOGGER.debug("rest.bad_request type={}", exception.getClass().getSimpleName());
    return invalidRequest();
  }

  /**
   * Raw {@link IllegalArgumentException}s still map to 400 because some domain invariants validate
   * client input (for example BCP 47 language overrides). They are logged with their stack trace so
   * that internal defects surfacing as {@code IllegalArgumentException} are no longer silent. New
   * client validations should throw {@link InvalidApplicationArgumentException} instead.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> unclassifiedIllegalArgument(IllegalArgumentException exception) {
    LOGGER.warn("rest.bad_request category=unclassified_illegal_argument", exception);
    return invalidRequest();
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The uploaded file is too large.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception) {
    if (exception instanceof ErrorResponse errorResponse
        && errorResponse.getStatusCode().is4xxClientError()) {
      // Framework-level client errors (unknown route, unsupported method or media type) keep their
      // HTTP status instead of being reported as internal failures.
      LOGGER.debug("rest.client_error type={}", exception.getClass().getSimpleName());
      return frameworkClientError(errorResponse.getStatusCode());
    }
    // The correlation id is already in the MDC, so this line is linked to the response requestId.
    LOGGER.error("rest.unexpected_error type={}", exception.getClass().getName(), exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.");
  }

  private ResponseEntity<ApiError> invalidRequest() {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.");
  }

  private ResponseEntity<ApiError> frameworkClientError(HttpStatusCode status) {
    return switch (status.value()) {
      case 404 -> error(status, "NOT_FOUND", "The requested resource was not found.");
      case 405 -> error(status, "METHOD_NOT_ALLOWED", "The request method is not supported.");
      case 406 -> error(status, "NOT_ACCEPTABLE", "The requested representation is not available.");
      case 415 ->
          error(status, "UNSUPPORTED_MEDIA_TYPE", "The request media type is not supported.");
      default -> error(status, "INVALID_REQUEST", "The request is invalid.");
    };
  }

  private ResponseEntity<ApiError> error(HttpStatusCode status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ApiError(code, message, Instant.now(), MDC.get("correlationId")));
  }

  private String message(DocumentImportException.Reason reason) {
    return switch (reason) {
      case INVALID_EPUB -> "The uploaded file is not a valid EPUB.";
      case INVALID_PDF -> "The uploaded file is not a valid PDF.";
      case PDF_PASSWORD_PROTECTED -> "Password-protected PDF files are not supported.";
      case PDF_SCANNED_NOT_SUPPORTED ->
          "The PDF contains no extractable text. Scanned PDFs are not supported.";
      case UNSUPPORTED_DRM -> "DRM-protected EPUB files are not supported.";
      case LANGUAGE_REQUIRED -> "The document language is required.";
      case UNSUPPORTED_LANGUAGE -> "The document language is not supported.";
      case FILE_TOO_LARGE -> "The uploaded file is too large.";
      case SECURITY_LIMIT_EXCEEDED -> "The EPUB exceeds a security limit.";
      case STORAGE_FAILURE, IMPORT_FAILURE -> "The document could not be imported.";
    };
  }

  public record ApiError(String code, String message, Instant timestamp, String requestId) {}
}
