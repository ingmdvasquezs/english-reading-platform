package com.soap.soap.application.model;

import com.soap.soap.domain.model.VocabularyStatus;
import java.util.UUID;

public record VocabularyReviewItem(
    UUID wordId, String word, String language, VocabularyStatus status) {}
