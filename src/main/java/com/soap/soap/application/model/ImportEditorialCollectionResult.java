package com.soap.soap.application.model;

import java.util.UUID;

public record ImportEditorialCollectionResult(
    UUID collectionId, String key, String title, boolean created, int membershipsCount) {}
