package com.soap.soap.application.model;

import com.soap.soap.domain.model.UserVocabulary;

public record UserVocabularyPage(PageResult<UserVocabulary> page, VocabularySummary summary) {}
