package com.soap.soap.application.port.in;

import com.soap.soap.application.model.ComprehensionQuizView;
import java.util.UUID;

public interface GetReadingComprehensionQuizPort {
  ComprehensionQuizView getQuiz(UUID readingId);
}
