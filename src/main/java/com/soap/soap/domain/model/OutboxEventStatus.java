package com.soap.soap.domain.model;

public enum OutboxEventStatus {
  PENDING,
  SENDING,
  PUBLISHED,
  FAILED
}
