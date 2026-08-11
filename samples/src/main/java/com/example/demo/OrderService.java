package com.example.demo;

import java.util.Collection;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Second demo source — overlaps with UserService on string utilities.
 */
public class OrderService {

  public boolean hasItems(Collection<?> items) {
    return !CollectionUtils.isEmpty(items);
  }

  public String normalizeCode(String code) {
    if (!StringUtils.hasText(code)) {
      return "";
    }
    return org.apache.commons.lang3.StringUtils.trim(code);
  }

  public boolean isEmptyCode(String code) {
    return StringUtils.isEmpty(code)
        || org.apache.commons.lang3.StringUtils.isBlank(code);
  }
}
