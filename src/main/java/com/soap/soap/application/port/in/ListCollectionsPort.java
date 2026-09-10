package com.soap.soap.application.port.in;

import com.soap.soap.domain.model.ReadingCollection;
import java.util.List;

public interface ListCollectionsPort {
  List<ReadingCollection> listCollections();
}
