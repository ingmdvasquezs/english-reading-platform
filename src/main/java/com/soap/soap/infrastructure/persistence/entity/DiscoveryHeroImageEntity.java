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
@Table(name = "discovery_country_hero_images")
public class DiscoveryHeroImageEntity extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "country_id", nullable = false)
  private DiscoveryCountryEntity country;

  @Column(name = "asset_key", nullable = false, length = 255)
  private String assetKey;

  @Column(length = 255)
  private String location;

  @Column(length = 255)
  private String alt;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;
}
