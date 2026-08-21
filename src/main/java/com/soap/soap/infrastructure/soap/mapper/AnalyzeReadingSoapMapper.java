package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.ReadingAnalysis;
import com.soap.soap.infrastructure.soap.generated.AnalyzeReadingRequest;
import com.soap.soap.infrastructure.soap.generated.AnalyzeReadingResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeReadingSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(AnalyzeReadingRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public AnalyzeReadingResponse toResponse(ReadingAnalysis analysis) {
    var response = new AnalyzeReadingResponse();
    response.setTotalTokens(analysis.totalTokens());
    response.setUniqueWords(analysis.uniqueWords());
    response.setKnownTokens(analysis.knownTokens());
    response.setKnownWords(analysis.knownWords());
    response.setLearningTokens(analysis.learningTokens());
    response.setLearningWords(analysis.learningWords());
    response.setUnknownTokens(analysis.unknownTokens());
    response.setUnknownWords(analysis.unknownWords());
    response.setIgnoredTokens(analysis.ignoredTokens());
    response.setIgnoredWords(analysis.ignoredWords());
    response.setPersonalizedCoveragePercentage(analysis.personalizedCoveragePercentage());
    analysis
        .frequentUnknownWords()
        .forEach(
            frequency -> {
              var result = new AnalyzeReadingResponse.UnknownWordFrequencies();
              result.setNormalizedValue(frequency.normalizedValue());
              result.setCount(frequency.count());
              response.getUnknownWordFrequencies().add(result);
            });
    return response;
  }
}
