package com.soap.soap.application.exception;

public class OnboardingAlreadyCompletedException extends RuntimeException {
  public OnboardingAlreadyCompletedException() {
    super("Initial vocabulary test has already been completed");
  }
}
