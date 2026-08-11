package com.example.demo;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.base.Preconditions;

/**
 * Demo source with overlapping string/collection utilities for Dependency Scan.
 */
public class UserService {

  public boolean isValidName(String name) {
    if (StringUtils.isBlank(name)) {
      return false;
    }
    if (Strings.isNullOrEmpty(name)) {
      return false;
    }
    return name.length() > 1;
  }

  public List<String> activeUsers(List<String> users) {
    Preconditions.checkNotNull(users);
    return Lists.newArrayList(users);
  }

  public String loadProfile(File file) throws IOException {
    return FileUtils.readFileToString(file, "UTF-8");
  }
}
