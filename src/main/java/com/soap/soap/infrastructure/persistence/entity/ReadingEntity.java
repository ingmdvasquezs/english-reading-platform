package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "readings")
public class ReadingEntity extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private UserEntity user;

  @Column(nullable = false, length = 250)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false, length = 10)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReadingOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(name = "editorial_level", length = 2)
  private EditorialLevel editorialLevel;

  @Column(length = 100)
  private String category;

  @Column(name = "cover_key", length = 120)
  private String coverKey;
}
