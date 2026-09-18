package com.soap.soap.domain.model;

public enum ReviewRating {
  AGAIN(1),
  HARD(2),
  GOOD(3),
  EASY(4);

  private final int grade;

  ReviewRating(int grade) {
    this.grade = grade;
  }

  public int getGrade() {
    return grade;
  }
}
