package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "words",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_words_normalized_value_language",
          columnNames = {"normalized_value", "language"})
    })
public class WordEntity extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "normalized_value", nullable = false, length = 100)
  private String normalizedValue;

  @Column(nullable = false, length = 10)
  private String language;
}
