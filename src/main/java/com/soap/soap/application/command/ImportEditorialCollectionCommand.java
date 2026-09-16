package com.soap.soap.application.command;

import java.util.List;

public record ImportEditorialCollectionCommand(
    String key,
    String title,
    String description,
    int displayOrder,
    boolean active,
    String coverKey,
    List<EditorialCollectionReadingItemCommand> readings) {}
