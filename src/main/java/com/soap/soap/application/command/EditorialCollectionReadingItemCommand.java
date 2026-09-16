package com.soap.soap.application.command;

import com.soap.soap.domain.model.EditorialLevel;

public record EditorialCollectionReadingItemCommand(
    String adaptationGroupKey, String language, EditorialLevel editorialLevel, int displayOrder) {}
