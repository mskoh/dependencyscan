import * as fs from "fs";
import * as path from "path";

export interface MethodUsage {
  library: string;
  typeName: string;
  method: string;
  line: number;
}

export interface RecommendationHit {
  typeName: string;
  method: string;
  line: number;
  prefer: string;
  note: string;
}

export interface SourceFinding {
  source: string;
  usages: MethodUsage[];
  recommendations: RecommendationHit[];
}

export interface DuplicateFinding {
  groupId: string;
  purpose: string;
  preferredType?: string;
  preferredLibrary?: string;
  recommendation?: string;
  types: string[];
  methods: string[];
  sources: string[];
  replacements: DuplicateReplacement[];
}

export interface DuplicateReplacement {
  source: string;
  line: number;
  current: string;
  recommended: string;
}

export interface LibrarySummary {
  library: string;
  types: string[];
  methodCount: number;
}

export interface ScanReport {
  scannedRoot: string;
  javaVersion: number | null;
  scannedFileCount: number;
  sources: SourceFinding[];
  libraries: LibrarySummary[];
  duplicates: DuplicateFinding[];
  generatedAt: string;
}

interface RecommendationRule {
  type: string;
  methods: string[];
  minJava: number;
  prefer: string;
  note: string;
  preferWhenJavaAtLeast?: number;
  preferForJava11Plus?: string;
}

interface DuplicateGroup {
  id: string;
  purpose: string;
  preferredType?: string;
  preferredLibrary?: string;
  recommendation?: string;
  members: string[];
  methodMembers?: string[];
  methodReplacements?: Record<string, string>;
  methodReplacementRules?: MethodReplacementRule[];
}

interface MethodReplacementRule {
  current: string;
  minJava?: number;
  maxJava?: number;
  recommended: string;
}

interface RecommendationCatalog {
  libraryAliases: Record<string, string>;
  duplicateGroups: DuplicateGroup[];
  recommendations: RecommendationRule[];
}

const IMPORT_RE = /^\s*import\s+(?:static\s+)?([a-zA-Z0-9_.]+)\s*;/;
const STATIC_IMPORT_METHOD_RE =
  /^\s*import\s+static\s+([a-zA-Z0-9_.]+)\.([a-zA-Z_][a-zA-Z0-9_]*)\s*;/;

function loadCatalog(catalogPath: string, customCatalogPath?: string): RecommendationCatalog {
  const raw = fs.readFileSync(catalogPath, "utf8");
  const catalog = JSON.parse(raw) as RecommendationCatalog;
  const customPath = customCatalogPath?.trim();
  if (!customPath) {
    return catalog;
  }
  if (!fs.existsSync(customPath)) {
    throw new Error(`Custom catalog not found: ${customPath}`);
  }
  const custom = JSON.parse(fs.readFileSync(customPath, "utf8")) as RecommendationCatalog;
  return mergeCatalog(catalog, custom);
}

function mergeCatalog(
  base: RecommendationCatalog,
  custom: RecommendationCatalog
): RecommendationCatalog {
  const duplicateGroups = [...(base.duplicateGroups ?? [])];
  for (const customGroup of custom.duplicateGroups ?? []) {
    const index = duplicateGroups.findIndex((group) => group.id === customGroup.id);
    if (index >= 0) {
      duplicateGroups[index] = customGroup;
    } else {
      duplicateGroups.push(customGroup);
    }
  }
  return {
    libraryAliases: {
      ...(base.libraryAliases ?? {}),
      ...(custom.libraryAliases ?? {}),
    },
    duplicateGroups,
    recommendations: [
      ...(base.recommendations ?? []),
      ...(custom.recommendations ?? []),
    ],
  };
}

function walkJavaFiles(dir: string): string[] {
  const results: string[] = [];
  if (!fs.existsSync(dir)) {
    return results;
  }
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "target" || entry.name === "build" || entry.name === "node_modules") {
        continue;
      }
      results.push(...walkJavaFiles(full));
    } else if (entry.isFile() && entry.name.endsWith(".java")) {
      results.push(full);
    }
  }
  return results;
}

function detectJavaVersion(projectRoot: string): number | null {
  const candidates = [
    path.join(projectRoot, "pom.xml"),
    path.join(projectRoot, "build.gradle"),
    path.join(projectRoot, "build.gradle.kts"),
    path.join(projectRoot, ".java-version"),
    path.join(projectRoot, ".settings", "org.eclipse.jdt.core.prefs"),
    path.join(projectRoot, ".classpath"),
  ];

  for (const file of candidates) {
    if (!fs.existsSync(file)) {
      continue;
    }
    const text = fs.readFileSync(file, "utf8");
    const patterns = [
      /<maven\.compiler\.(?:source|release)>\s*(\d+)\s*</,
      /<java\.version>\s*(\d+)\s*</,
      /sourceCompatibility\s*[=:]\s*['"]?(\d+)/,
      /targetCompatibility\s*[=:]\s*['"]?(\d+)/,
      /JavaLanguageVersion\.of\((\d+)\)/,
      /org\.eclipse\.jdt\.core\.compiler\.compliance=(\d+)/,
      /org\.eclipse\.jdt\.core\.compiler\.source=(\d+)/,
      /JavaSE-(\d+)/,
      /^(\d+)\s*$/m,
    ];
    for (const re of patterns) {
      const m = text.match(re);
      if (m) {
        return Number(m[1]);
      }
    }
  }

  // Walk up a few levels from scanned root
  let current = projectRoot;
  for (let i = 0; i < 4; i++) {
    const parent = path.dirname(current);
    if (parent === current) {
      break;
    }
    current = parent;
    const nested = detectJavaVersionShallow(current);
    if (nested !== null) {
      return nested;
    }
  }
  return null;
}

function detectJavaVersionShallow(projectRoot: string): number | null {
  for (const name of [
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    path.join(".settings", "org.eclipse.jdt.core.prefs"),
    ".classpath",
  ]) {
    const file = path.join(projectRoot, name);
    if (!fs.existsSync(file)) {
      continue;
    }
    const text = fs.readFileSync(file, "utf8");
    const m =
      text.match(/<maven\.compiler\.(?:source|release)>\s*(\d+)\s*</) ||
      text.match(/<java\.version>\s*(\d+)\s*</) ||
      text.match(/sourceCompatibility\s*[=:]\s*['"]?(\d+)/) ||
      text.match(/org\.eclipse\.jdt\.core\.compiler\.compliance=(\d+)/) ||
      text.match(/JavaSE-(\d+)/);
    if (m) {
      return Number(m[1]);
    }
  }
  return null;
}

function resolveLibrary(typeName: string, aliases: Record<string, string>): string {
  const sorted = Object.keys(aliases).sort((a, b) => b.length - a.length);
  for (const prefix of sorted) {
    if (typeName === prefix || typeName.startsWith(prefix + ".")) {
      return aliases[prefix];
    }
  }
  const parts = typeName.split(".");
  if (parts.length >= 3) {
    return parts.slice(0, 3).join(".");
  }
  return typeName;
}

function simpleName(typeName: string): string {
  const parts = typeName.split(".");
  return parts[parts.length - 1];
}

function parseJavaFile(
  filePath: string,
  catalog: RecommendationCatalog,
  javaVersion: number | null,
  rootForRelative: string
): SourceFinding {
  const content = fs.readFileSync(filePath, "utf8");
  const lines = content.split(/\r?\n/);

  const imports = new Map<string, string>(); // simple name -> FQN
  const staticMethods = new Map<string, { typeName: string; method: string }>();

  for (const line of lines) {
    const staticMatch = line.match(STATIC_IMPORT_METHOD_RE);
    if (staticMatch) {
      staticMethods.set(staticMatch[2], {
        typeName: staticMatch[1],
        method: staticMatch[2],
      });
      continue;
    }
    const importMatch = line.match(IMPORT_RE);
    if (importMatch) {
      const fqn = importMatch[1];
      if (fqn.endsWith(".*")) {
        continue;
      }
      imports.set(simpleName(fqn), fqn);
    }
  }

  const usages: MethodUsage[] = [];
  const recommendations: RecommendationHit[] = [];
  const callRe = /(?<!\.)\b([A-Z][A-Za-z0-9_]*)\s*\.\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/g;
  const fqnCallRe =
    /\b((?:[a-z][a-zA-Z0-9_]*\.)+[A-Z][A-Za-z0-9_]*)\s*\.\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/g;
  const staticCallRe = /\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/g;

  const considerUsage = (typeName: string, method: string, lineNo: number) => {
    const isExternal =
      !typeName.startsWith("java.") &&
      !typeName.startsWith("javax.") &&
      !typeName.startsWith("jakarta.");
    const hasRule = catalog.recommendations.some((r) => r.type === typeName);
    if (!isExternal && !hasRule) {
      return;
    }
    usages.push({
      library: resolveLibrary(typeName, catalog.libraryAliases),
      typeName,
      method,
      line: lineNo,
    });
    maybeAddRecommendation(
      recommendations,
      catalog,
      typeName,
      method,
      lineNo,
      javaVersion
    );
  };

  lines.forEach((line, idx) => {
    const lineNo = idx + 1;
    if (/^\s*\/\//.test(line) || /^\s*\*/.test(line) || /^\s*import\s+/.test(line)) {
      return;
    }

    let match: RegExpExecArray | null;
    fqnCallRe.lastIndex = 0;
    while ((match = fqnCallRe.exec(line)) !== null) {
      considerUsage(match[1], match[2], lineNo);
    }

    callRe.lastIndex = 0;
    while ((match = callRe.exec(line)) !== null) {
      const receiver = match[1];
      const method = match[2];
      const typeName = imports.get(receiver);
      if (!typeName) {
        continue;
      }
      considerUsage(typeName, method, lineNo);
    }

    // static imports: methodName(
    if (staticMethods.size > 0) {
      staticCallRe.lastIndex = 0;
      while ((match = staticCallRe.exec(line)) !== null) {
        const method = match[1];
        const info = staticMethods.get(method);
        if (!info) {
          continue;
        }
        usages.push({
          library: resolveLibrary(info.typeName, catalog.libraryAliases),
          typeName: info.typeName,
          method: info.method,
          line: lineNo,
        });
        maybeAddRecommendation(
          recommendations,
          catalog,
          info.typeName,
          info.method,
          lineNo,
          javaVersion
        );
      }
    }
  });

  // Deduplicate identical usages on same line
  const usageKey = (u: MethodUsage) => `${u.typeName}#${u.method}@${u.line}`;
  const uniqueUsages = [...new Map(usages.map((u) => [usageKey(u), u])).values()];

  return {
    source: path.relative(rootForRelative, filePath) || path.basename(filePath),
    usages: uniqueUsages,
    recommendations,
  };
}

function maybeAddRecommendation(
  out: RecommendationHit[],
  catalog: RecommendationCatalog,
  typeName: string,
  method: string,
  line: number,
  javaVersion: number | null
): void {
  for (const rule of catalog.recommendations) {
    if (rule.type !== typeName) {
      continue;
    }
    if (rule.methods.length > 0 && !rule.methods.includes(method)) {
      continue;
    }
    const version = javaVersion ?? 8;
    if (version < rule.minJava) {
      continue;
    }
    let prefer = rule.prefer;
    if (
      rule.preferWhenJavaAtLeast &&
      rule.preferForJava11Plus &&
      version >= rule.preferWhenJavaAtLeast
    ) {
      prefer = rule.preferForJava11Plus;
    }
    out.push({
      typeName,
      method,
      line,
      prefer,
      note: rule.note,
    });
  }
}

function buildLibrarySummary(sources: SourceFinding[]): LibrarySummary[] {
  const map = new Map<string, { types: Set<string>; methodCount: number }>();
  for (const source of sources) {
    for (const usage of source.usages) {
      let entry = map.get(usage.library);
      if (!entry) {
        entry = { types: new Set(), methodCount: 0 };
        map.set(usage.library, entry);
      }
      entry.types.add(usage.typeName);
      entry.methodCount += 1;
    }
  }
  return [...map.entries()]
    .map(([library, v]) => ({
      library,
      types: [...v.types].sort(),
      methodCount: v.methodCount,
    }))
    .sort((a, b) => b.methodCount - a.methodCount);
}

function findDuplicates(
  sources: SourceFinding[],
  catalog: RecommendationCatalog,
  javaVersion: number | null
): DuplicateFinding[] {
  const findings: DuplicateFinding[] = [];
  for (const group of catalog.duplicateGroups) {
    const usedTypes = new Set<string>();
    const usedMethods = new Set<string>();
    const usedSources = new Set<string>();
    for (const source of sources) {
      for (const usage of source.usages) {
        if (matchesDuplicateGroup(group, usage)) {
          usedTypes.add(usage.typeName);
          usedMethods.add(`${usage.typeName}#${usage.method}`);
          usedSources.add(source.source);
        }
      }
    }
    const duplicateCount = group.methodMembers?.length ? usedMethods.size : usedTypes.size;
    if (duplicateCount >= 2) {
      findings.push({
        groupId: group.id,
        purpose: group.purpose,
        preferredType: group.preferredType,
        preferredLibrary: group.preferredLibrary,
        recommendation: group.recommendation,
        types: [...usedTypes].sort(),
        methods: [...usedMethods].sort(),
        sources: [...usedSources].sort(),
        replacements: buildDuplicateReplacements(group, sources, javaVersion),
      });
    }
  }
  return findings;
}

function buildDuplicateReplacements(
  group: DuplicateGroup,
  sources: SourceFinding[],
  javaVersion: number | null
): DuplicateReplacement[] {
  const replacements: DuplicateReplacement[] = [];
  for (const source of sources) {
    for (const usage of source.usages) {
      if (!group.members.includes(usage.typeName)) {
        continue;
      }
      if (!matchesDuplicateGroup(group, usage)) {
        continue;
      }
      if (group.preferredType && usage.typeName === group.preferredType) {
        continue;
      }
      const recommended = resolveDuplicateReplacement(group, usage, javaVersion);
      if (!recommended) {
        continue;
      }
      replacements.push({
        source: source.source,
        line: usage.line,
        current: `${usage.typeName}#${usage.method}`,
        recommended,
      });
    }
  }
  return replacements;
}

function resolveDuplicateReplacement(
  group: DuplicateGroup,
  usage: MethodUsage,
  javaVersion: number | null
): string | undefined {
  const exactKey = `${usage.typeName}#${usage.method}`;
  const version = javaVersion ?? 8;
  for (const rule of group.methodReplacementRules ?? []) {
    if (rule.current !== exactKey) {
      continue;
    }
    if (version < (rule.minJava ?? 8)) {
      continue;
    }
    if (rule.maxJava !== undefined && version > rule.maxJava) {
      continue;
    }
    return rule.recommended;
  }
  const replacements = group.methodReplacements ?? {};
  if (replacements[exactKey]) {
    return replacements[exactKey];
  }
  return undefined;
}

function matchesDuplicateGroup(group: DuplicateGroup, usage: MethodUsage): boolean {
  if (group.methodMembers?.length) {
    return group.methodMembers.includes(`${usage.typeName}#${usage.method}`);
  }
  return group.members.includes(usage.typeName);
}

export function findProjectRoot(startDir: string): string {
  let current = path.resolve(startDir);
  for (let i = 0; i < 8; i++) {
    if (
      fs.existsSync(path.join(current, "pom.xml")) ||
      fs.existsSync(path.join(current, "build.gradle")) ||
      fs.existsSync(path.join(current, "build.gradle.kts")) ||
      fs.existsSync(path.join(current, ".git"))
    ) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      break;
    }
    current = parent;
  }
  return path.resolve(startDir);
}

/**
 * Build a report filename from the selected scan path relative to the project root.
 * e.g. src/main/java -> src_main_java.md
 */
export function buildReportFileName(projectRoot: string, scannedRoot: string): string {
  const project = path.resolve(projectRoot);
  const scanned = path.resolve(scannedRoot);
  let rel = path.relative(project, scanned);
  if (!rel || rel === "." || rel.startsWith("..")) {
    rel = path.basename(scanned);
  }
  const safe = rel
    .replace(/\\/g, "/")
    .replace(/\/+/g, "_")
    .replace(/[^a-zA-Z0-9._-]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return `${safe || "scan"}.md`;
}

export function writeReportFile(
  projectRoot: string,
  scannedRoot: string,
  markdown: string,
  reportDirectory = "reports"
): string {
  const configured = reportDirectory.trim() || "reports";
  const reportsDir = path.isAbsolute(configured)
    ? path.normalize(configured)
    : path.join(path.resolve(projectRoot), configured);
  fs.mkdirSync(reportsDir, { recursive: true });
  const fileName = buildReportFileName(projectRoot, scannedRoot);
  const reportPath = path.join(reportsDir, fileName);
  fs.writeFileSync(reportPath, markdown, "utf8");
  return reportPath;
}

export function scanDirectory(
  scannedRoot: string,
  catalogPath: string,
  customCatalogPath?: string
): ScanReport {
  const catalog = loadCatalog(catalogPath, customCatalogPath);
  const projectRoot = findProjectRoot(scannedRoot);
  const javaVersion = detectJavaVersion(projectRoot);
  const files = walkJavaFiles(scannedRoot);
  const sources = files
    .map((f) => parseJavaFile(f, catalog, javaVersion, projectRoot))
    .filter((s) => s.usages.length > 0 || s.recommendations.length > 0);

  // Also include files with zero external usages for completeness? User asked to list sources with methods.
  // Keep only files that have dependency usages.
  return {
    scannedRoot,
    javaVersion,
    scannedFileCount: files.length,
    sources,
    libraries: buildLibrarySummary(sources),
    duplicates: findDuplicates(sources, catalog, javaVersion),
    generatedAt: new Date().toISOString(),
  };
}

export function formatReportMarkdown(report: ScanReport): string {
  const lines: string[] = [];
  lines.push(`# Dependency Scan Report`);
  lines.push("");
  lines.push(`- Scanned root: \`${report.scannedRoot}\``);
  lines.push(`- Java version: ${report.javaVersion ?? "unknown"}`);
  lines.push(`- Files scanned: ${report.scannedFileCount}`);
  lines.push(`- Generated: ${report.generatedAt}`);
  lines.push("");

  lines.push(`## Libraries in use`);
  lines.push("");
  if (report.libraries.length === 0) {
    lines.push("_No external library method usages detected._");
  } else {
    lines.push(`| Library | Types | Method calls |`);
    lines.push(`|---|---|---|`);
    for (const lib of report.libraries) {
      lines.push(
        `| ${lib.library} | ${lib.types.map((t) => `\`${simpleName(t)}\``).join(", ")} | ${lib.methodCount} |`
      );
    }
  }
  lines.push("");

  lines.push(`## Duplicates`);
  lines.push("");
  if (report.duplicates.length === 0) {
    lines.push("_No overlapping utility libraries detected._");
  } else {
    for (const dup of report.duplicates) {
      lines.push(`### ${dup.purpose} (\`${dup.groupId}\`)`);
      lines.push(`- Types: ${dup.types.map((t) => `\`${t}\``).join(", ")}`);
      if (dup.methods.length > 0) {
        lines.push(`- Matched methods: ${dup.methods.map((m) => `\`${m}\``).join(", ")}`);
      }
      if (dup.preferredType) {
        const library = dup.preferredLibrary ? ` (${dup.preferredLibrary})` : "";
        lines.push(`- Recommended type: \`${dup.preferredType}\`${library}`);
      }
      if (dup.recommendation) {
        lines.push(`- Recommendation: ${dup.recommendation}`);
      }
      lines.push(`- Sources: ${dup.sources.map((s) => `\`${s}\``).join(", ")}`);
      lines.push("");
      if (dup.replacements.length > 0) {
        lines.push(`| Source | Line | Current | Recommended |`);
        lines.push(`|---|---:|---|---|`);
        for (const replacement of dup.replacements) {
          lines.push(
            `| \`${replacement.source}\` | ${replacement.line} | \`${replacement.current}\` | \`${replacement.recommended}\` |`
          );
        }
        lines.push("");
      }
    }
  }

  lines.push(`## Findings by source`);
  lines.push("");
  if (report.sources.length === 0) {
    lines.push("_No dependency method usages found._");
  } else {
    for (const source of report.sources) {
      lines.push(`### \`${source.source}\``);
      lines.push("");
      lines.push(`| Line | Library | Method | Recommendation |`);
      lines.push(`|---|---|---|---|`);
      const recByKey = new Map(
        source.recommendations.map((r) => [`${r.typeName}#${r.method}@${r.line}`, r])
      );
      for (const usage of source.usages) {
        const key = `${usage.typeName}#${usage.method}@${usage.line}`;
        const rec = recByKey.get(key);
        const method = `${simpleName(usage.typeName)}.${usage.method}()`;
        const recommendation = rec
          ? `${rec.prefer} — ${rec.note}`
          : "—";
        lines.push(
          `| ${usage.line} | ${usage.library} | \`${method}\` | ${recommendation} |`
        );
      }
      lines.push("");
    }
  }

  return lines.join("\n");
}
