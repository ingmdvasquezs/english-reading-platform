package com.soap.soap.application.port.in;

import com.soap.soap.application.command.SetVocabularyStatusCommand;
import com.soap.soap.domain.model.UserVocabulary;

public interface SetVocabularyStatusPort {
  UserVocabulary setVocabularyStatus(SetVocabularyStatusCommand command);
}
