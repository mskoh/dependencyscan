import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import { formatReportMarkdown, scanDirectory, ScanReport } from "./scanner";

export function activate(context: vscode.ExtensionContext): void {
  const disposable = vscode.commands.registerCommand(
    "dependencyscan.scan",
    async (uri?: vscode.Uri) => {
      const target = await resolveTargetDirectory(uri);
      if (!target) {
        vscode.window.showWarningMessage(
          "Dependency Scan: 스캔할 폴더를 선택하세요. (예: src/main/java)"
        );
        return;
      }

      const catalogPath = resolveCatalogPath(context);
      if (!fs.existsSync(catalogPath)) {
        vscode.window.showErrorMessage(
          `Dependency Scan: 추천 카탈로그를 찾을 수 없습니다: ${catalogPath}`
        );
        return;
      }

      await vscode.window.withProgress(
        {
          location: vscode.ProgressLocation.Notification,
          title: "Dependency Scan 실행 중…",
          cancellable: false,
        },
        async () => {
          const report = scanDirectory(target.fsPath, catalogPath);
          await showReport(report);
        }
      );
    }
  );

  context.subscriptions.push(disposable);
}

export function deactivate(): void {
  // no-op
}

async function resolveTargetDirectory(
  uri?: vscode.Uri
): Promise<vscode.Uri | undefined> {
  if (uri && uri.scheme === "file") {
    const stat = await vscode.workspace.fs.stat(uri);
    if (stat.type & vscode.FileType.Directory) {
      return uri;
    }
    return vscode.Uri.file(path.dirname(uri.fsPath));
  }

  const picked = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    openLabel: "Scan",
  });
  return picked?.[0];
}

function resolveCatalogPath(context: vscode.ExtensionContext): string {
  const candidates = [
    path.join(context.extensionPath, "..", "core", "recommendations.json"),
    path.join(context.extensionPath, "recommendations.json"),
    path.join(__dirname, "..", "..", "core", "recommendations.json"),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return candidates[0];
}

async function showReport(report: ScanReport): Promise<void> {
  const markdown = formatReportMarkdown(report);
  const doc = await vscode.workspace.openTextDocument({
    content: markdown,
    language: "markdown",
  });
  await vscode.window.showTextDocument(doc, { preview: false });

  const panel = vscode.window.createWebviewPanel(
    "dependencyscanReport",
    "Dependency Scan Report",
    vscode.ViewColumn.Beside,
    { enableScripts: false }
  );
  panel.webview.html = renderHtml(report);

  const dupCount = report.duplicates.length;
  const usageCount = report.sources.reduce((n, s) => n + s.usages.length, 0);
  vscode.window.showInformationMessage(
    `Dependency Scan 완료: 파일 ${report.scannedFileCount}개, 사용 ${usageCount}건, 중복 그룹 ${dupCount}개`
  );
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function simpleName(typeName: string): string {
  const parts = typeName.split(".");
  return parts[parts.length - 1];
}

function renderHtml(report: ScanReport): string {
  const libraryRows = report.libraries
    .map(
      (lib) =>
        `<tr><td>${escapeHtml(lib.library)}</td><td>${escapeHtml(
          lib.types.map(simpleName).join(", ")
        )}</td><td>${lib.methodCount}</td></tr>`
    )
    .join("");

  const duplicateBlocks =
    report.duplicates.length === 0
      ? "<p class='muted'>중복 사용 없음</p>"
      : report.duplicates
          .map(
            (d) => `
      <div class="card">
        <h3>${escapeHtml(d.purpose)}</h3>
        <p><strong>Types:</strong> ${d.types.map((t) => `<code>${escapeHtml(t)}</code>`).join(", ")}</p>
        <p><strong>Sources:</strong> ${d.sources.map((s) => `<code>${escapeHtml(s)}</code>`).join(", ")}</p>
      </div>`
          )
          .join("");

  const sourceBlocks = report.sources
    .map((source) => {
      const recMap = new Map(
        source.recommendations.map((r) => [`${r.typeName}#${r.method}@${r.line}`, r])
      );
      const rows = source.usages
        .map((u) => {
          const rec = recMap.get(`${u.typeName}#${u.method}@${u.line}`);
          const method = `${simpleName(u.typeName)}.${u.method}()`;
          const recommendation = rec
            ? `${escapeHtml(rec.prefer)} <span class="note">${escapeHtml(rec.note)}</span>`
            : "—";
          return `<tr>
            <td>${u.line}</td>
            <td>${escapeHtml(u.library)}</td>
            <td><code>${escapeHtml(method)}</code></td>
            <td>${recommendation}</td>
          </tr>`;
        })
        .join("");
      return `
        <section class="source">
          <h3><code>${escapeHtml(source.source)}</code></h3>
          <table>
            <thead>
              <tr><th>Line</th><th>Library</th><th>Method</th><th>Recommendation</th></tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </section>`;
    })
    .join("");

  return `<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <style>
    :root {
      --bg: #f7f4ef;
      --ink: #1c1917;
      --muted: #78716c;
      --line: #d6d3d1;
      --accent: #0f766e;
      --card: #ffffff;
    }
    body {
      font-family: "IBM Plex Sans", "Noto Sans KR", sans-serif;
      color: var(--ink);
      background:
        radial-gradient(circle at top left, #ccfbf1 0%, transparent 40%),
        linear-gradient(180deg, #f7f4ef 0%, #efe8dc 100%);
      margin: 0;
      padding: 24px 28px 48px;
      line-height: 1.45;
    }
    h1 { font-size: 1.6rem; margin: 0 0 8px; letter-spacing: -0.02em; }
    h2 { font-size: 1.15rem; margin: 28px 0 12px; color: var(--accent); }
    h3 { font-size: 1rem; margin: 0 0 8px; }
    .meta { color: var(--muted); font-size: 0.92rem; margin-bottom: 20px; }
    .meta code { background: #fff; padding: 1px 6px; border-radius: 4px; }
    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 8px;
      overflow: hidden;
      margin-bottom: 12px;
    }
    th, td {
      text-align: left;
      padding: 8px 10px;
      border-bottom: 1px solid var(--line);
      vertical-align: top;
      font-size: 0.9rem;
    }
    th { background: #ecfdf5; color: #115e59; }
    code { font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 0.85em; }
    .note { display: block; color: var(--muted); font-size: 0.82rem; margin-top: 2px; }
    .card {
      background: var(--card);
      border: 1px solid var(--line);
      border-left: 4px solid #dc2626;
      border-radius: 8px;
      padding: 12px 14px;
      margin-bottom: 10px;
    }
    .source { margin-bottom: 22px; }
    .muted { color: var(--muted); }
  </style>
</head>
<body>
  <h1>Dependency Scan Report</h1>
  <div class="meta">
    <div>Root: <code>${escapeHtml(report.scannedRoot)}</code></div>
    <div>Java: <strong>${report.javaVersion ?? "unknown"}</strong> · Files: <strong>${report.scannedFileCount}</strong> · ${escapeHtml(report.generatedAt)}</div>
  </div>

  <h2>Libraries</h2>
  <table>
    <thead><tr><th>Library</th><th>Types</th><th>Calls</th></tr></thead>
    <tbody>${libraryRows || '<tr><td colspan="3" class="muted">없음</td></tr>'}</tbody>
  </table>

  <h2>Duplicates</h2>
  ${duplicateBlocks}

  <h2>Sources · Methods · Recommendations</h2>
  ${sourceBlocks || '<p class="muted">탐지된 사용 없음</p>'}
</body>
</html>`;
}
