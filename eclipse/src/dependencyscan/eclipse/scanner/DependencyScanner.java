package dependencyscan.eclipse.scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dependencyscan.eclipse.model.ScanReport;
import dependencyscan.eclipse.model.ScanReport.DuplicateFinding;
import dependencyscan.eclipse.model.ScanReport.DuplicateReplacement;
import dependencyscan.eclipse.model.ScanReport.LibrarySummary;
import dependencyscan.eclipse.model.ScanReport.MethodUsage;
import dependencyscan.eclipse.model.ScanReport.RecommendationHit;
import dependencyscan.eclipse.model.ScanReport.SourceFinding;

/**
 * Lightweight Java source scanner (regex-based) for dependency method usage.
 * Mirrors the VS Code scanner logic so both IDEs produce comparable reports.
 */
public class DependencyScanner {

  private static final Pattern IMPORT_RE =
      Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z0-9_.]+)\\s*;");
  private static final Pattern STATIC_IMPORT_METHOD_RE =
      Pattern.compile("^\\s*import\\s+static\\s+([a-zA-Z0-9_.]+)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*;");
  private static final Pattern CALL_RE =
      Pattern.compile("(?<!\\.)\\b([A-Z][A-Za-z0-9_]*)\\s*\\.\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
  private static final Pattern FQN_CALL_RE =
      Pattern.compile("\\b((?:[a-z][a-zA-Z0-9_]*\\.)+[A-Z][A-Za-z0-9_]*)\\s*\\.\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");
  private static final Pattern STATIC_CALL_RE =
      Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(");

  private final RecommendationCatalog catalog;

  public DependencyScanner(RecommendationCatalog catalog) {
    this.catalog = catalog;
  }

  public ScanReport scan(Path scannedRoot) throws IOException {
    return scan(scannedRoot, null);
  }

  public ScanReport scan(Path scannedRoot, Integer fallbackJavaVersion) throws IOException {
    Path projectRoot = findProjectRoot(scannedRoot);
    Integer javaVersion = detectJavaVersion(projectRoot);
    if (javaVersion == null) {
      javaVersion = fallbackJavaVersion;
    }

    List<Path> javaFiles;
    try (Stream<Path> walk = Files.walk(scannedRoot)) {
      javaFiles = walk
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> {
            String s = p.toString().replace('\\', '/');
            return !s.contains("/target/") && !s.contains("/build/");
          })
          .sorted()
          .collect(Collectors.toList());
    }

    ScanReport report = new ScanReport();
    report.scannedRoot = scannedRoot.toAbsolutePath().toString();
    report.javaVersion = javaVersion;
    report.scannedFileCount = javaFiles.size();
    report.generatedAt = Instant.now().toString();

    for (Path file : javaFiles) {
      SourceFinding finding = parseJavaFile(file, projectRoot, javaVersion);
      if (!finding.usages.isEmpty() || !finding.recommendations.isEmpty()) {
        report.sources.add(finding);
      }
    }

    report.libraries.addAll(buildLibrarySummary(report.sources));
    report.duplicates.addAll(findDuplicates(report.sources, javaVersion));
    return report;
  }

  private SourceFinding parseJavaFile(Path file, Path projectRoot, Integer javaVersion)
      throws IOException {
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    Map<String, String> imports = new HashMap<>();
    Map<String, String[]> staticMethods = new HashMap<>();

    for (String line : lines) {
      Matcher staticMatch = STATIC_IMPORT_METHOD_RE.matcher(line);
      if (staticMatch.find()) {
        staticMethods.put(staticMatch.group(2),
            new String[] { staticMatch.group(1), staticMatch.group(2) });
        continue;
      }
      Matcher importMatch = IMPORT_RE.matcher(line);
      if (importMatch.find()) {
        String fqn = importMatch.group(1);
        if (!fqn.endsWith(".*")) {
          imports.put(simpleName(fqn), fqn);
        }
      }
    }

    SourceFinding finding = new SourceFinding();
    finding.source = projectRoot.relativize(file.toAbsolutePath()).toString().replace('\\', '/');

    Set<String> seen = new HashSet<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int lineNo = i + 1;
      if (line.trim().startsWith("//") || line.trim().startsWith("*") || line.trim().startsWith("import ")) {
        continue;
      }

      Matcher fqnCall = FQN_CALL_RE.matcher(line);
      while (fqnCall.find()) {
        addUsage(finding, seen, fqnCall.group(1), fqnCall.group(2), lineNo, javaVersion);
      }

      Matcher call = CALL_RE.matcher(line);
      while (call.find()) {
        String receiver = call.group(1);
        String method = call.group(2);
        String typeName = imports.get(receiver);
        if (typeName == null) {
          continue;
        }
        addUsage(finding, seen, typeName, method, lineNo, javaVersion);
      }

      if (!staticMethods.isEmpty()) {
        Matcher staticCall = STATIC_CALL_RE.matcher(line);
        while (staticCall.find()) {
          String method = staticCall.group(1);
          String[] info = staticMethods.get(method);
          if (info == null) {
            continue;
          }
          addUsage(finding, seen, info[0], info[1], lineNo, javaVersion);
        }
      }
    }
    return finding;
  }

  private void addUsage(
      SourceFinding finding,
      Set<String> seen,
      String typeName,
      String method,
      int lineNo,
      Integer javaVersion) {
    boolean isExternal = !typeName.startsWith("java.")
        && !typeName.startsWith("javax.")
        && !typeName.startsWith("jakarta.");
    boolean hasRule = catalog.hasRecommendation(typeName);
    if (!isExternal && !hasRule) {
      return;
    }
    String key = typeName + "#" + method + "@" + lineNo;
    if (!seen.add(key)) {
      return;
    }
    MethodUsage usage = new MethodUsage();
    usage.library = catalog.resolveLibrary(typeName);
    usage.typeName = typeName;
    usage.method = method;
    usage.line = lineNo;
    finding.usages.add(usage);
    addRecommendation(finding, typeName, method, lineNo, javaVersion);
  }

  private void addRecommendation(
      SourceFinding finding,
      String typeName,
      String method,
      int line,
      Integer javaVersion) {
    for (RecommendationCatalog.Rule rule : catalog.getRecommendations()) {
      if (!rule.type.equals(typeName)) {
        continue;
      }
      if (!rule.methods.isEmpty() && !rule.methods.contains(method)) {
        continue;
      }
      int version = javaVersion != null ? javaVersion : 8;
      if (version < rule.minJava) {
        continue;
      }
      RecommendationHit hit = new RecommendationHit();
      hit.typeName = typeName;
      hit.method = method;
      hit.line = line;
      hit.prefer = rule.prefer;
      if (rule.preferWhenJavaAtLeast != null
          && rule.preferForJava11Plus != null
          && version >= rule.preferWhenJavaAtLeast) {
        hit.prefer = rule.preferForJava11Plus;
      }
      hit.note = rule.note;
      finding.recommendations.add(hit);
    }
  }

  private List<LibrarySummary> buildLibrarySummary(List<SourceFinding> sources) {
    Map<String, LibrarySummary> map = new LinkedHashMap<>();
    Map<String, Set<String>> types = new HashMap<>();
    for (SourceFinding source : sources) {
      for (MethodUsage usage : source.usages) {
        LibrarySummary summary = map.computeIfAbsent(usage.library, lib -> {
          LibrarySummary s = new LibrarySummary();
          s.library = lib;
          return s;
        });
        summary.methodCount++;
        types.computeIfAbsent(usage.library, k -> new LinkedHashSet<>()).add(usage.typeName);
      }
    }
    List<LibrarySummary> list = new ArrayList<>();
    for (Map.Entry<String, LibrarySummary> e : map.entrySet()) {
      LibrarySummary s = e.getValue();
      s.types.addAll(types.getOrDefault(e.getKey(), Set.of()));
      s.types.sort(Comparator.naturalOrder());
      list.add(s);
    }
    list.sort((a, b) -> Integer.compare(b.methodCount, a.methodCount));
    return list;
  }

  private List<DuplicateFinding> findDuplicates(List<SourceFinding> sources, Integer javaVersion) {
    List<DuplicateFinding> findings = new ArrayList<>();
    for (RecommendationCatalog.DuplicateGroup group : catalog.getDuplicateGroups()) {
      Set<String> usedTypes = new LinkedHashSet<>();
      Set<String> usedMethods = new LinkedHashSet<>();
      Set<String> usedSources = new LinkedHashSet<>();
      for (SourceFinding source : sources) {
        for (MethodUsage usage : source.usages) {
          if (matchesDuplicateGroup(group, usage)) {
            usedTypes.add(usage.typeName);
            usedMethods.add(usage.typeName + "#" + usage.method);
            usedSources.add(source.source);
          }
        }
      }
      int duplicateCount = group.methodMembers.isEmpty() ? usedTypes.size() : usedMethods.size();
      if (duplicateCount >= 2) {
        DuplicateFinding dup = new DuplicateFinding();
        dup.groupId = group.id;
        dup.purpose = group.purpose;
        dup.preferredType = group.preferredType;
        dup.preferredLibrary = group.preferredLibrary;
        dup.recommendation = group.recommendation;
        dup.types.addAll(usedTypes);
        dup.methods.addAll(usedMethods);
        dup.sources.addAll(usedSources);
        dup.replacements.addAll(buildDuplicateReplacements(group, sources, javaVersion));
        findings.add(dup);
      }
    }
    return findings;
  }

  private List<DuplicateReplacement> buildDuplicateReplacements(
      RecommendationCatalog.DuplicateGroup group,
      List<SourceFinding> sources,
      Integer javaVersion) {
    List<DuplicateReplacement> replacements = new ArrayList<>();
    for (SourceFinding source : sources) {
      for (MethodUsage usage : source.usages) {
        if (!group.members.contains(usage.typeName)) {
          continue;
        }
        if (!matchesDuplicateGroup(group, usage)) {
          continue;
        }
        if (usage.typeName.equals(group.preferredType)) {
          continue;
        }
        String recommended = resolveDuplicateReplacement(group, usage, javaVersion);
        if (recommended == null || recommended.isBlank()) {
          continue;
        }
        DuplicateReplacement replacement = new DuplicateReplacement();
        replacement.source = source.source;
        replacement.line = usage.line;
        replacement.current = usage.typeName + "#" + usage.method;
        replacement.recommended = recommended;
        replacements.add(replacement);
      }
    }
    return replacements;
  }

  private String resolveDuplicateReplacement(
      RecommendationCatalog.DuplicateGroup group,
      MethodUsage usage,
      Integer javaVersion) {
    String key = usage.typeName + "#" + usage.method;
    int version = javaVersion != null ? javaVersion : 8;
    for (RecommendationCatalog.MethodReplacementRule rule : group.methodReplacementRules) {
      if (!key.equals(rule.current)) {
        continue;
      }
      if (version < rule.minJava) {
        continue;
      }
      if (rule.maxJava != null && version > rule.maxJava) {
        continue;
      }
      return rule.recommended;
    }
    String replacement = group.methodReplacements.get(key);
    if (replacement != null) {
      return replacement;
    }
    return null;
  }

  private boolean matchesDuplicateGroup(
      RecommendationCatalog.DuplicateGroup group,
      MethodUsage usage) {
    if (!group.methodMembers.isEmpty()) {
      return group.methodMembers.contains(usage.typeName + "#" + usage.method);
    }
    return group.members.contains(usage.typeName);
  }

  public static Path findProjectRoot(Path startDir) {
    Path current = startDir.toAbsolutePath().normalize();
    for (int i = 0; i < 8; i++) {
      if (Files.exists(current.resolve("pom.xml"))
          || Files.exists(current.resolve("build.gradle"))
          || Files.exists(current.resolve("build.gradle.kts"))
          || Files.exists(current.resolve(".git"))) {
        return current;
      }
      Path parent = current.getParent();
      if (parent == null) {
        break;
      }
      current = parent;
    }
    return startDir.toAbsolutePath().normalize();
  }

  public static Integer detectJavaVersion(Path projectRoot) throws IOException {
    String[] names = { "pom.xml", "build.gradle", "build.gradle.kts", ".java-version" };
    Pattern[] patterns = {
        Pattern.compile("<maven\\.compiler\\.(?:source|release)>\\s*(\\d+)\\s*<"),
        Pattern.compile("<java\\.version>\\s*(\\d+)\\s*<"),
        Pattern.compile("sourceCompatibility\\s*[=:]\\s*['\"]?(\\d+)"),
        Pattern.compile("targetCompatibility\\s*[=:]\\s*['\"]?(\\d+)"),
        Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)"),
        Pattern.compile("^(\\d+)\\s*$", Pattern.MULTILINE)
    };

    Path current = projectRoot;
    for (int depth = 0; depth < 5; depth++) {
      for (String name : names) {
        Path file = current.resolve(name);
        if (!Files.exists(file)) {
          continue;
        }
        String text = Files.readString(file);
        for (Pattern p : patterns) {
          Matcher m = p.matcher(text);
          if (m.find()) {
            return Integer.parseInt(m.group(1));
          }
        }
      }
      Path parent = current.getParent();
      if (parent == null) {
        break;
      }
      current = parent;
    }
    return null;
  }

  public static String simpleName(String typeName) {
    int idx = typeName.lastIndexOf('.');
    return idx >= 0 ? typeName.substring(idx + 1) : typeName;
  }

  public static String formatMarkdown(ScanReport report) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Dependency Scan Report\n\n");
    sb.append("- Scanned root: `").append(report.scannedRoot).append("`\n");
    sb.append("- Java version: ")
        .append(report.javaVersion != null ? report.javaVersion : "unknown")
        .append("\n");
    sb.append("- Files scanned: ").append(report.scannedFileCount).append("\n");
    sb.append("- Generated: ").append(report.generatedAt).append("\n\n");

    sb.append("## Libraries in use\n\n");
    if (report.libraries.isEmpty()) {
      sb.append("_No external library method usages detected._\n\n");
    } else {
      sb.append("| Library | Types | Method calls |\n|---|---|---|\n");
      for (LibrarySummary lib : report.libraries) {
        sb.append("| ").append(lib.library).append(" | ");
        sb.append(lib.types.stream().map(DependencyScanner::simpleName)
            .map(t -> "`" + t + "`").collect(Collectors.joining(", ")));
        sb.append(" | ").append(lib.methodCount).append(" |\n");
      }
      sb.append("\n");
    }

    sb.append("## Duplicates\n\n");
    if (report.duplicates.isEmpty()) {
      sb.append("_No overlapping utility libraries detected._\n\n");
    } else {
      for (DuplicateFinding dup : report.duplicates) {
        sb.append("### ").append(dup.purpose).append(" (`").append(dup.groupId).append("`)\n");
        sb.append("- Types: ")
            .append(dup.types.stream().map(t -> "`" + t + "`").collect(Collectors.joining(", ")))
            .append("\n");
        if (!dup.methods.isEmpty()) {
          sb.append("- Matched methods: ")
              .append(dup.methods.stream().map(s -> "`" + s + "`").collect(Collectors.joining(", ")))
              .append("\n");
        }
        if (dup.preferredType != null && !dup.preferredType.isBlank()) {
          sb.append("- Recommended type: `").append(dup.preferredType).append("`");
          if (dup.preferredLibrary != null && !dup.preferredLibrary.isBlank()) {
            sb.append(" (").append(dup.preferredLibrary).append(")");
          }
          sb.append("\n");
        }
        if (dup.recommendation != null && !dup.recommendation.isBlank()) {
          sb.append("- Recommendation: ").append(dup.recommendation).append("\n");
        }
        sb.append("- Sources: ")
            .append(dup.sources.stream().map(s -> "`" + s + "`").collect(Collectors.joining(", ")))
            .append("\n\n");
        if (!dup.replacements.isEmpty()) {
          sb.append("| Source | Line | Current | Recommended |\n|---|---:|---|---|\n");
          for (DuplicateReplacement replacement : dup.replacements) {
            sb.append("| `").append(replacement.source).append("` | ")
                .append(replacement.line)
                .append(" | `").append(replacement.current).append("` | `")
                .append(replacement.recommended).append("` |\n");
          }
          sb.append("\n");
        }
      }
    }

    sb.append("## Findings by source\n\n");
    for (SourceFinding source : report.sources) {
      sb.append("### `").append(source.source).append("`\n\n");
      sb.append("| Line | Library | Method | Recommendation |\n|---|---|---|---|\n");
      Map<String, RecommendationHit> recMap = new HashMap<>();
      for (RecommendationHit r : source.recommendations) {
        recMap.put(r.typeName + "#" + r.method + "@" + r.line, r);
      }
      for (MethodUsage usage : source.usages) {
        String key = usage.typeName + "#" + usage.method + "@" + usage.line;
        RecommendationHit rec = recMap.get(key);
        String method = simpleName(usage.typeName) + "." + usage.method + "()";
        String recommendation = rec != null ? rec.prefer + " — " + rec.note : "—";
        sb.append("| ").append(usage.line).append(" | ").append(usage.library)
            .append(" | `").append(method).append("` | ").append(recommendation).append(" |\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public static RecommendationCatalog loadBundledCatalog() throws IOException {
    InputStream in = DependencyScanner.class.getResourceAsStream("/recommendations.json");
    if (in == null) {
      // Fall back to sibling core catalog when running from source tree
      Path fallback = Path.of("..", "core", "recommendations.json").toAbsolutePath().normalize();
      if (Files.exists(fallback)) {
        return RecommendationCatalog.fromJson(Files.readString(fallback));
      }
      throw new IOException("recommendations.json not found in bundle");
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String json = reader.lines().collect(Collectors.joining("\n"));
      return RecommendationCatalog.fromJson(json);
    }
  }

  public static void mergeCustomCatalog(RecommendationCatalog catalog, String customCatalogPath)
      throws IOException {
    String value = customCatalogPath == null ? "" : customCatalogPath.trim();
    if (value.isEmpty()) {
      return;
    }
    Path customPath = Path.of(value).toAbsolutePath().normalize();
    if (!Files.exists(customPath)) {
      throw new IOException("Custom catalog not found: " + customPath);
    }
    RecommendationCatalog custom = RecommendationCatalog.fromJson(Files.readString(customPath));
    catalog.mergeFrom(custom);
  }
}
