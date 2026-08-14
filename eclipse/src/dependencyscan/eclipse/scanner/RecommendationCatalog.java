package dependencyscan.eclipse.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON catalog loader (no external JSON dependency for the PDE plugin).
 */
public class RecommendationCatalog {

  public static class Rule {
    public String type;
    public final List<String> methods = new ArrayList<>();
    public int minJava;
    public String prefer;
    public String note;
    public Integer preferWhenJavaAtLeast;
    public String preferForJava11Plus;
  }

  public static class DuplicateGroup {
    public String id;
    public String purpose;
    public String preferredType;
    public String preferredLibrary;
    public String recommendation;
    public final List<String> members = new ArrayList<>();
    public final List<String> methodMembers = new ArrayList<>();
    public final Map<String, String> methodReplacements = new LinkedHashMap<>();
    public final List<MethodReplacementRule> methodReplacementRules = new ArrayList<>();
  }

  public static class MethodReplacementRule {
    public String current;
    public int minJava;
    public Integer maxJava;
    public String recommended;
  }

  private final Map<String, String> libraryAliases = new LinkedHashMap<>();
  private final List<DuplicateGroup> duplicateGroups = new ArrayList<>();
  private final List<Rule> recommendations = new ArrayList<>();

  public List<DuplicateGroup> getDuplicateGroups() {
    return Collections.unmodifiableList(duplicateGroups);
  }

  public List<Rule> getRecommendations() {
    return Collections.unmodifiableList(recommendations);
  }

  public void mergeFrom(RecommendationCatalog custom) {
    if (custom == null) {
      return;
    }
    libraryAliases.putAll(custom.libraryAliases);
    for (DuplicateGroup customGroup : custom.duplicateGroups) {
      int existingIndex = -1;
      for (int i = 0; i < duplicateGroups.size(); i++) {
        if (duplicateGroups.get(i).id != null && duplicateGroups.get(i).id.equals(customGroup.id)) {
          existingIndex = i;
          break;
        }
      }
      if (existingIndex >= 0) {
        duplicateGroups.set(existingIndex, customGroup);
      } else {
        duplicateGroups.add(customGroup);
      }
    }
    recommendations.addAll(custom.recommendations);
  }

  public boolean hasRecommendation(String typeName) {
    for (Rule rule : recommendations) {
      if (rule.type.equals(typeName)) {
        return true;
      }
    }
    return false;
  }

  public String resolveLibrary(String typeName) {
    String best = null;
    String bestPrefix = "";
    for (Map.Entry<String, String> e : libraryAliases.entrySet()) {
      String prefix = e.getKey();
      if ((typeName.equals(prefix) || typeName.startsWith(prefix + "."))
          && prefix.length() > bestPrefix.length()) {
        bestPrefix = prefix;
        best = e.getValue();
      }
    }
    if (best != null) {
      return best;
    }
    String[] parts = typeName.split("\\.");
    if (parts.length >= 3) {
      return parts[0] + "." + parts[1] + "." + parts[2];
    }
    return typeName;
  }

  public static RecommendationCatalog fromJson(String json) {
    RecommendationCatalog catalog = new RecommendationCatalog();
    catalog.libraryAliases.putAll(parseStringMap(extractObject(json, "libraryAliases")));
    catalog.duplicateGroups.addAll(parseDuplicateGroups(extractArray(json, "duplicateGroups")));
    catalog.recommendations.addAll(parseRules(extractArray(json, "recommendations")));
    return catalog;
  }

  private static Map<String, String> parseStringMap(String objectBody) {
    Map<String, String> map = new LinkedHashMap<>();
    if (objectBody == null) {
      return map;
    }
    Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(objectBody);
    while (m.find()) {
      map.put(m.group(1), m.group(2));
    }
    return map;
  }

  private static List<DuplicateGroup> parseDuplicateGroups(String arrayBody) {
    List<DuplicateGroup> groups = new ArrayList<>();
    if (arrayBody == null) {
      return groups;
    }
    for (String obj : splitTopLevelObjects(arrayBody)) {
      DuplicateGroup g = new DuplicateGroup();
      g.id = readString(obj, "id");
      g.purpose = readString(obj, "purpose");
      g.preferredType = readString(obj, "preferredType");
      g.preferredLibrary = readString(obj, "preferredLibrary");
      g.recommendation = readString(obj, "recommendation");
      g.members.addAll(readStringArray(obj, "members"));
      g.methodMembers.addAll(readStringArray(obj, "methodMembers"));
      g.methodReplacements.putAll(parseStringMap(extractObject(obj, "methodReplacements")));
      g.methodReplacementRules.addAll(parseMethodReplacementRules(extractArray(obj, "methodReplacementRules")));
      groups.add(g);
    }
    return groups;
  }

  private static List<MethodReplacementRule> parseMethodReplacementRules(String arrayBody) {
    List<MethodReplacementRule> rules = new ArrayList<>();
    if (arrayBody == null) {
      return rules;
    }
    for (String obj : splitTopLevelObjects(arrayBody)) {
      MethodReplacementRule rule = new MethodReplacementRule();
      rule.current = readString(obj, "current");
      rule.minJava = readInt(obj, "minJava", 8);
      rule.maxJava = readNullableInt(obj, "maxJava");
      rule.recommended = readString(obj, "recommended");
      if (rule.current != null && rule.recommended != null) {
        rules.add(rule);
      }
    }
    return rules;
  }

  private static List<Rule> parseRules(String arrayBody) {
    List<Rule> rules = new ArrayList<>();
    if (arrayBody == null) {
      return rules;
    }
    for (String obj : splitTopLevelObjects(arrayBody)) {
      Rule rule = new Rule();
      rule.type = readString(obj, "type");
      rule.methods.addAll(readStringArray(obj, "methods"));
      rule.minJava = readInt(obj, "minJava", 8);
      rule.prefer = readString(obj, "prefer");
      rule.note = readString(obj, "note");
      Integer preferWhen = readNullableInt(obj, "preferWhenJavaAtLeast");
      rule.preferWhenJavaAtLeast = preferWhen;
      rule.preferForJava11Plus = readString(obj, "preferForJava11Plus");
      rules.add(rule);
    }
    return rules;
  }

  private static String extractObject(String json, String key) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{");
    Matcher m = p.matcher(json);
    if (!m.find()) {
      return null;
    }
    return sliceBalanced(json, m.end() - 1, '{', '}');
  }

  private static String extractArray(String json, String key) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
    Matcher m = p.matcher(json);
    if (!m.find()) {
      return null;
    }
    return sliceBalanced(json, m.end() - 1, '[', ']');
  }

  private static String sliceBalanced(String text, int openIdx, char open, char close) {
    int depth = 0;
    boolean inString = false;
    for (int i = openIdx; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
        inString = !inString;
      }
      if (inString) {
        continue;
      }
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return text.substring(openIdx + 1, i);
        }
      }
    }
    return null;
  }

  private static List<String> splitTopLevelObjects(String arrayBody) {
    List<String> objects = new ArrayList<>();
    int depth = 0;
    boolean inString = false;
    int start = -1;
    for (int i = 0; i < arrayBody.length(); i++) {
      char c = arrayBody.charAt(i);
      if (c == '"' && (i == 0 || arrayBody.charAt(i - 1) != '\\')) {
        inString = !inString;
      }
      if (inString) {
        continue;
      }
      if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          objects.add(arrayBody.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return objects;
  }

  private static String readString(String obj, String key) {
    Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .matcher(obj);
    if (m.find()) {
      return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return null;
  }

  private static int readInt(String obj, String key, int defaultValue) {
    Integer value = readNullableInt(obj, key);
    return value != null ? value : defaultValue;
  }

  private static Integer readNullableInt(String obj, String key) {
    Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(obj);
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    return null;
  }

  private static List<String> readStringArray(String obj, String key) {
    List<String> values = new ArrayList<>();
    Matcher arr = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[").matcher(obj);
    if (!arr.find()) {
      return values;
    }
    String body = sliceBalanced(obj, arr.end() - 1, '[', ']');
    if (body == null) {
      return values;
    }
    Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(body);
    while (m.find()) {
      values.add(m.group(1));
    }
    return values;
  }
}
