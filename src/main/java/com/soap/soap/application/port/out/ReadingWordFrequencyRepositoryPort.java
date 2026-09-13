package com.soap.soap.application.port.out;

import com.soap.soap.application.model.ReadingLexicalEvidence;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReadingWordFrequencyRepositoryPort {

  void replaceFrequencies(UUID readingId, String language, Map<String, Integer> frequencies);

  void deleteByReadingId(UUID readingId);

  Map<String, Integer> findFrequenciesByReadingId(UUID readingId);

  List<ReadingLexicalEvidence> findLexicalEvidenceByUserAndLanguage(
      UUID userId, String language, Collection<UUID> readingIds);
}
