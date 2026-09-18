package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "discovery_regions")
public class DiscoveryRegionEntity extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "region_key", nullable = false, unique = true, length = 50)
  private String key;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(length = 255)
  private String subtitle;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active = true;

  @OneToMany(mappedBy = "region", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("displayOrder ASC")
  private List<DiscoveryCountryEntity> countries = new ArrayList<>();
}
