#!/usr/bin/env node
/**
 * CLI helper: node src/vscode/scripts/scan-cli.js <dir>
 */
const path = require("path");
const { scanDirectory, formatReportMarkdown } = require("../out/scanner");

const target = process.argv[2];
if (!target) {
  console.error("Usage: node scan-cli.js <java-source-directory>");
  process.exit(1);
}

const catalog = path.resolve(__dirname, "../../core/recommendations.json");
const report = scanDirectory(path.resolve(target), catalog);
console.log(formatReportMarkdown(report));
