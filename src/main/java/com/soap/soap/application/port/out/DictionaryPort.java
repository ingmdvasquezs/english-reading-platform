package com.soap.soap.application.port.out;

import com.soap.soap.application.model.DictionaryEntry;

public interface DictionaryPort {
  DictionaryEntry lookup(String word, String language);
}
