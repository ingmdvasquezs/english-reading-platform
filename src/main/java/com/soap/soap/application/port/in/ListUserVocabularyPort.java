package com.soap.soap.application.port.in;

import com.soap.soap.application.model.UserVocabularyPage;
import com.soap.soap.application.model.VocabularyQuery;

public interface ListUserVocabularyPort {
  UserVocabularyPage listUserVocabulary(VocabularyQuery query);
}
