package dependencyscan.eclipse.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScanReport {
  public String scannedRoot;
  public Integer javaVersion;
  public int scannedFileCount;
  public final List<SourceFinding> sources = new ArrayList<>();
  public final List<LibrarySummary> libraries = new ArrayList<>();
  public final List<DuplicateFinding> duplicates = new ArrayList<>();
  public String generatedAt;

  public static class MethodUsage {
    public String library;
    public String typeName;
    public String method;
    public int line;
  }

  public static class RecommendationHit {
    public String typeName;
    public String method;
    public int line;
    public String prefer;
    public String note;
  }

  public static class SourceFinding {
    public String source;
    public final List<MethodUsage> usages = new ArrayList<>();
    public final List<RecommendationHit> recommendations = new ArrayList<>();
  }

  public static class DuplicateFinding {
    public String groupId;
    public String purpose;
    public final List<String> types = new ArrayList<>();
    public final List<String> sources = new ArrayList<>();
  }

  public static class LibrarySummary {
    public String library;
    public final List<String> types = new ArrayList<>();
    public int methodCount;
  }

  public Map<String, Object> toOutlineMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("scannedRoot", scannedRoot);
    map.put("javaVersion", javaVersion);
    map.put("scannedFileCount", scannedFileCount);
    map.put("sourceCount", sources.size());
    map.put("duplicateCount", duplicates.size());
    return map;
  }
}
