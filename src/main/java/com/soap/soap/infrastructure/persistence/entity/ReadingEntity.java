package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
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

  @Column(nullable = false, length = 50)
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

  @Enumerated(EnumType.STRING)
  @Column(name = "editorial_status", length = 20)
  private EditorialStatus editorialStatus;

  @Column(name = "short_description", length = 500)
  private String shortDescription;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", length = 50)
  private EditorialContentType contentType;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "region", length = 50)
  private EditorialRegion region;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_kind", length = 50)
  private SourceKind sourceKind;

  @Enumerated(EnumType.STRING)
  @Column(name = "rights_status", length = 50)
  private RightsStatus rightsStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "adaptation_kind", length = 50)
  private AdaptationKind adaptationKind;

  @Column(name = "source_language", length = 50)
  private String sourceLanguage;

  @Column(name = "source_title", length = 250)
  private String sourceTitle;

  @Column(name = "source_author", length = 150)
  private String sourceAuthor;

  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  @Column(name = "source_notes", columnDefinition = "TEXT")
  private String sourceNotes;

  @Column(name = "adaptation_group_key", length = 100)
  private String adaptationGroupKey;

  @Column(name = "cover_attribution", length = 250)
  private String coverAttribution;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_tier", length = 20)
  private AccessTier accessTier;
}
