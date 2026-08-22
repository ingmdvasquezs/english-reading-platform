package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.WordLookup;
import com.soap.soap.infrastructure.soap.generated.LookupWordRequest;
import com.soap.soap.infrastructure.soap.generated.LookupWordResponse;
import org.springframework.stereotype.Component;

@Component
public class LookupWordSoapMapper {
  public String toWord(LookupWordRequest request) {
    return request.getWord();
  }

  public LookupWordResponse toResponse(WordLookup lookup) {
    var response = new LookupWordResponse();
    response.setWord(lookup.word());
    response.setNormalizedWord(lookup.normalizedWord());
    response.setTranslation(lookup.translation());
    response.setPhonetic(lookup.phonetic());
    response.setAudioUrl(lookup.audioUrl());
    lookup
        .meanings()
        .forEach(
            meaning -> {
              var soapMeaning = new LookupWordResponse.Meanings();
              soapMeaning.setPartOfSpeech(meaning.partOfSpeech());
              meaning
                  .definitions()
                  .forEach(
                      definition -> {
                        var soapDefinition = new LookupWordResponse.Meanings.Definitions();
                        soapDefinition.setDefinition(definition.definition());
                        soapDefinition.setExample(definition.example());
                        soapMeaning.getDefinitions().add(soapDefinition);
                      });
              response.getMeanings().add(soapMeaning);
            });
    return response;
  }
}
