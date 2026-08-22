package com.soap.soap.infrastructure.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LibreTranslateResponse(String translatedText) {}
