package com.soap.soap.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserVocabulary(
    UUID id,
    User user,
    Word word,
    VocabularyStatus status,
    LocalDateTime firstSeenAt,
    LocalDateTime learnedAt) {}
