package com.soap.soap.application.port.in;

import com.soap.soap.application.model.CollectionSummary;
import java.util.List;

public interface ListCollectionsPort {
  List<CollectionSummary> listCollections();
}
