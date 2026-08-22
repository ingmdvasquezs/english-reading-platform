package com.soap.soap.application.model;

import java.util.List;
import java.util.UUID;

public record ReadingReaderData(
    UUID readingId, String title, String language, List<ReaderToken> tokens) {}
