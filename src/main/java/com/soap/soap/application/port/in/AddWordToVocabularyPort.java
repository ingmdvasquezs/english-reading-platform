package com.soap.soap.application.port.in;

import com.soap.soap.application.command.AddWordToVocabularyCommand;
import com.soap.soap.domain.model.UserVocabulary;

public interface AddWordToVocabularyPort {
  UserVocabulary addWordToVocabulary(AddWordToVocabularyCommand command);
}
