package com.soap.soap.application.model;

import java.util.UUID;

public record DocumentSectionNavigation(
    UUID id, int ordinal, String title, UUID firstUnitId, long unitCount) {}
