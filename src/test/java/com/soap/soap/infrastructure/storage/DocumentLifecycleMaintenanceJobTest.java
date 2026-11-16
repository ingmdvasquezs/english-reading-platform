package com.soap.soap.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.service.ExpiredUploadCleanupService;
import com.soap.soap.application.service.StaleImportRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DocumentLifecycleMaintenanceJobTest {
  private final ExpiredUploadCleanupService expiredUploads =
      mock(ExpiredUploadCleanupService.class);
  private final StaleImportRecoveryService staleImports = mock(StaleImportRecoveryService.class);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ExpiredUploadCleanupService.class, () -> expiredUploads)
          .withBean(StaleImportRecoveryService.class, () -> staleImports)
          .withUserConfiguration(DocumentLifecycleMaintenanceJob.class);

  @Test
  void aFailureInOneCategoryDoesNotSkipTheOther() {
    when(expiredUploads.cleanupExpiredUploads()).thenThrow(new IllegalStateException("db down"));
    when(staleImports.recoverStaleImports())
        .thenReturn(new StaleImportRecoveryService.Result(1, 0));

    new DocumentLifecycleMaintenanceJob(expiredUploads, staleImports).run();

    verify(staleImports).recoverStaleImports();
  }

  @Test
  void runsOnlyWhenEnabledAndInTheApiOrAllRole() {
    runner
        .withPropertyValues("app.document-lifecycle.enabled=true", "app.role=api")
        .run(context -> assertThat(context).hasSingleBean(DocumentLifecycleMaintenanceJob.class));
    runner
        .withPropertyValues("app.document-lifecycle.enabled=true", "app.role=all")
        .run(context -> assertThat(context).hasSingleBean(DocumentLifecycleMaintenanceJob.class));
    runner
        .withPropertyValues("app.document-lifecycle.enabled=true", "app.role=worker")
        .run(context -> assertThat(context).doesNotHaveBean(DocumentLifecycleMaintenanceJob.class));
    runner
        .withPropertyValues("app.document-lifecycle.enabled=false", "app.role=api")
        .run(context -> assertThat(context).doesNotHaveBean(DocumentLifecycleMaintenanceJob.class));
  }
}
