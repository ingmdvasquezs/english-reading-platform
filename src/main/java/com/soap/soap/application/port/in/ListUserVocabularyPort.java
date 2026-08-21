package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.UserVocabulary;

public interface ListUserVocabularyPort {
  PageResult<UserVocabulary> listUserVocabulary(PageRequest pageRequest);
}
