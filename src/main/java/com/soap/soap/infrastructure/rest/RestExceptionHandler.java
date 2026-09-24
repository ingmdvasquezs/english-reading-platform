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
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.soap.soap.infrastructure.rest")
public class RestExceptionHandler {
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

  @ExceptionHandler({
    IllegalArgumentException.class,
    InvalidApplicationArgumentException.class,
    MethodArgumentNotValidException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> badRequest(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The uploaded file is too large.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
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
