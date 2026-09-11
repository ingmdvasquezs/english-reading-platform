package com.soap.soap.application.model;

public record WordDefinition(String definition, String example, String exampleTranslation) {

  public WordDefinition(String definition, String example) {
    this(definition, example, null);
  }
}
