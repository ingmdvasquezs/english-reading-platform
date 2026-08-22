package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.command.AddWordToVocabularyCommand;
import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.application.command.RegisterUserCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionBoundaryTest {
  @Test
  void cpuHeavyAndCredentialWorkDoesNotOpenAnApplicationTransaction() throws Exception {
    assertNotTransactional(LoginUseCase.class, "login", LoginCommand.class);
    assertNotTransactional(RegisterUserUseCase.class, "registerUser", RegisterUserCommand.class);
    assertNotTransactional(AnalyzeReadingUseCase.class, "analyzeReading", UUID.class);
    assertNotTransactional(GetReadingReaderDataUseCase.class, "getReadingReaderData", UUID.class);
  }

  @Test
  void modifyingMultiStepWorkflowsRemainTransactional() throws Exception {
    assertThat(
            AddWordToVocabularyUseCase.class
                .getMethod("addWordToVocabulary", AddWordToVocabularyCommand.class)
                .getAnnotation(Transactional.class))
        .isNotNull();
    assertThat(
            RegisterReadingUseCase.class
                .getMethod("registerReading", RegisterReadingCommand.class)
                .getAnnotation(Transactional.class))
        .isNotNull();
  }

  private void assertNotTransactional(Class<?> type, String method, Class<?> parameter)
      throws Exception {
    assertThat(type.getMethod(method, parameter).getAnnotation(Transactional.class)).isNull();
  }
}
