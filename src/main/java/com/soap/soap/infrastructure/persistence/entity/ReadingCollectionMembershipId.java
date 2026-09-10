package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ReadingCollectionMembershipId(
    @Column(name = "collection_id") UUID collectionId, @Column(name = "reading_id") UUID readingId)
    implements Serializable {}
