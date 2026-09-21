package com.soap.soap.application.model;

public record GetDiscoveryHomeQuery(int maxContinueReading, int maxForYou, int maxShelfReadings) {

  public static final int DEFAULT_MAX_CONTINUE_READING = 10;
  public static final int MIN_CONTINUE_READING = 1;
  public static final int MAX_CONTINUE_READING = 20;

  public static final int DEFAULT_MAX_FOR_YOU = 8;
  public static final int MIN_FOR_YOU = 1;
  public static final int MAX_FOR_YOU = 20;

  public static final int DEFAULT_MAX_SHELF_READINGS = 8;
  public static final int MIN_SHELF_READINGS = 1;
  public static final int MAX_SHELF_READINGS = 12;

  public GetDiscoveryHomeQuery {
    maxContinueReading =
        clamp(
            maxContinueReading,
            DEFAULT_MAX_CONTINUE_READING,
            MIN_CONTINUE_READING,
            MAX_CONTINUE_READING);
    maxForYou = clamp(maxForYou, DEFAULT_MAX_FOR_YOU, MIN_FOR_YOU, MAX_FOR_YOU);
    maxShelfReadings =
        clamp(maxShelfReadings, DEFAULT_MAX_SHELF_READINGS, MIN_SHELF_READINGS, MAX_SHELF_READINGS);
  }

  private static int clamp(int value, int defaultValue, int min, int max) {
    if (value <= 0) {
      return defaultValue;
    }
    return Math.min(Math.max(value, min), max);
  }
}
