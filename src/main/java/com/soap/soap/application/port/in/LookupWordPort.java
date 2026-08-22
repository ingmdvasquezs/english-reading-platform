package com.soap.soap.application.port.in;

import com.soap.soap.application.model.WordLookup;

public interface LookupWordPort {
  WordLookup lookupWord(String word);
}
