package com.soap.soap.application.model;

public record VocabularyBreakdown(
    int uniqueWords,
    int knownWords,
    int learningWords,
    int explicitNewWords,
    int ignoredWords,
    int unclassifiedWords) {}
