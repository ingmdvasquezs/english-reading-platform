package com.soap.soap.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WorkerClaim(WorkerClaimResult result, Optional<ImportJob> job, UUID leaseToken) {

  public WorkerClaim {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(job, "job must not be null");
  }

  public static WorkerClaim acquired(ImportJob job, UUID leaseToken) {
    Objects.requireNonNull(job, "job must not be null");
    Objects.requireNonNull(leaseToken, "leaseToken must not be null");
    return new WorkerClaim(WorkerClaimResult.ACQUIRED, Optional.of(job), leaseToken);
  }

  public static WorkerClaim rejected(WorkerClaimResult result) {
    if (result == WorkerClaimResult.ACQUIRED) {
      throw new IllegalArgumentException("Cannot reject with ACQUIRED result");
    }
    return new WorkerClaim(result, Optional.empty(), null);
  }

  public boolean isAcquired() {
    return result == WorkerClaimResult.ACQUIRED;
  }
}
