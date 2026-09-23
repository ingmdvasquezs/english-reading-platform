package com.soap.soap.domain.model;

public enum WorkerClaimResult {
  ACQUIRED,
  ACTIVE_BY_OTHER_WORKER,
  ALREADY_COMPLETED,
  FINAL_FAILED,
  ABORTED,
  RETRY_LIMIT_EXCEEDED,
  NOT_FOUND
}
