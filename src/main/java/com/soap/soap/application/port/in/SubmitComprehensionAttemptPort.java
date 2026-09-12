package com.soap.soap.application.port.in;

import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.model.ComprehensionAttemptResult;

public interface SubmitComprehensionAttemptPort {
  ComprehensionAttemptResult submitAttempt(SubmitComprehensionAttemptCommand command);
}
