/**
 * Shared TypeScript types for Dependency Scan reports.
 * Consumed by the VS Code extension; Eclipse mirrors these shapes in Java.
 */

export interface MethodUsage {
  library: string;
  typeName: string;
  method: string;
  line: number;
}

export interface SourceFinding {
  source: string;
  usages: MethodUsage[];
  recommendations: RecommendationHit[];
}

export interface RecommendationHit {
  typeName: string;
  method: string;
  line: number;
  prefer: string;
  note: string;
}

export interface DuplicateFinding {
  groupId: string;
  purpose: string;
  types: string[];
  sources: string[];
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
