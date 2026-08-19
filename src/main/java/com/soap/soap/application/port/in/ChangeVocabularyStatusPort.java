package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.UUID;

public interface ChangeVocabularyStatusPort {
  UserVocabulary changeVocabularyStatus(UUID userId, UUID wordId, VocabularyStatus status);
}
