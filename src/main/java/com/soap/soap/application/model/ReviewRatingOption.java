package com.soap.soap.application.model;

import com.soap.soap.domain.model.ReviewRating;
import java.time.LocalDateTime;

public record ReviewRatingOption(
    ReviewRating rating, LocalDateTime nextReviewAt, long intervalSeconds) {}
