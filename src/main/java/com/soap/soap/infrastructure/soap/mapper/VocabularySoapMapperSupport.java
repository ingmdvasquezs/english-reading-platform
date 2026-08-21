package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.generated.VocabularyEntryType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;

abstract class VocabularySoapMapperSupport extends SoapMapperSupport {
  protected VocabularyStatus toStatus(VocabularyStatusType status) {
    if (status == null) {
      throw new InvalidSoapRequestException("Vocabulary status must not be null", null);
    }
    return VocabularyStatus.valueOf(status.value());
  }

  protected VocabularyEntryType toEntry(UserVocabulary vocabulary) {
    var entry = new VocabularyEntryType();
    if (vocabulary.id() != null) {
      entry.setEntryId(vocabulary.id().toString());
    }
    entry.setUserId(vocabulary.user().id().toString());
    entry.setWordId(vocabulary.word().id().toString());
    entry.setWord(vocabulary.word().normalizedValue());
    entry.setLanguage(vocabulary.word().language());
    entry.setStatus(VocabularyStatusType.fromValue(vocabulary.status().name()));
    entry.setFirstSeenAt(toXmlDate(vocabulary.firstSeenAt()));
    entry.setLearnedAt(toXmlDate(vocabulary.learnedAt()));
    return entry;
  }
}
