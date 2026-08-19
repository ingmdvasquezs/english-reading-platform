package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.UserVocabulary;
import java.util.UUID;

public interface ListUserVocabularyPort {
  PageResult<UserVocabulary> listUserVocabulary(UUID userId, PageRequest pageRequest);
}
