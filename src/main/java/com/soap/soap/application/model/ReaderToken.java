package com.soap.soap.application.model;

import com.soap.soap.domain.model.VocabularyStatus;

public record ReaderToken(
    String value, String normalizedValue, ReaderTokenType type, VocabularyStatus status) {}
