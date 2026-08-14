package dependencyscan.eclipse.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportPaths {

  private ReportPaths() {}

  /**
   * Build a report filename from the selected scan path relative to the project root.
   * e.g. src/main/java -> src_main_java.md
   */
  public static String buildReportFileName(Path projectRoot, Path scannedRoot) {
    Path project = projectRoot.toAbsolutePath().normalize();
    Path scanned = scannedRoot.toAbsolutePath().normalize();
    Path rel;
    try {
      rel = project.relativize(scanned);
    } catch (IllegalArgumentException ex) {
      rel = scanned.getFileName();
    }
    String value = rel == null ? "" : rel.toString().replace('\\', '/');
    if (value.isEmpty() || value.equals(".")) {
      value = scanned.getFileName() != null ? scanned.getFileName().toString() : "scan";
    }
    if (value.startsWith("../") || value.equals("..")) {
      value = scanned.getFileName() != null ? scanned.getFileName().toString() : "scan";
    }
    String safe = value
        .replace('/', '_')
        .replaceAll("[^a-zA-Z0-9._-]+", "_")
        .replaceAll("^_+|_+$", "");
    if (safe.isEmpty()) {
      safe = "scan";
    }
    return safe + ".md";
  }

  public static Path writeReport(Path projectRoot, Path scannedRoot, String markdown)
      throws IOException {
    return writeReport(projectRoot, scannedRoot, "reports", markdown);
  }

  public static Path writeReport(
      Path projectRoot,
      Path scannedRoot,
      String reportDirectory,
      String markdown) throws IOException {
    Path reportsDir = resolveReportDirectory(projectRoot, reportDirectory);
    Files.createDirectories(reportsDir);
    Path reportFile = reportsDir.resolve(buildReportFileName(projectRoot, scannedRoot));
    Files.writeString(reportFile, markdown, StandardCharsets.UTF_8);
    return reportFile;
  }

  public static Path resolveReportDirectory(Path projectRoot, String reportDirectory) {
    String value = reportDirectory == null ? "" : reportDirectory.trim();
    if (value.isEmpty()) {
      value = "reports";
    }
    Path configured = Path.of(value);
    if (configured.isAbsolute()) {
      return configured.normalize();
    }
    return projectRoot.toAbsolutePath().normalize().resolve(configured).normalize();
  }
}
