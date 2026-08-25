import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const root = path.resolve(process.argv[2] ?? defaultRoot);
const manifestPath = path.join(root, ".harness", "manifest.json");
const errors = [];
const warnings = [];

const fail = (message) => errors.push(message);
const warn = (message) => warnings.push(message);
const exists = (relative) => fs.existsSync(path.join(root, relative));

let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
} catch (error) {
  fail(`Cannot parse .harness/manifest.json: ${error.message}`);
  manifest = {};
}

const allowedStatuses = new Set(["observed", "declared", "inferred", "unknown"]);
const tier = manifest?.harness?.tier ?? "core";
const docsRoot = manifest?.harness?.docsRoot ?? "docs/harness";
const instructionEntry = manifest?.harness?.instructionEntry ?? "AGENTS.md";
const knowledgeRoot = manifest?.knowledge?.root ?? "docs/project-knowledge";

const coreDocs = ["README.md", "change-protocol.md", "testing-guide.md", "templates.md"];
const standardDocs = ["quality-rules.md", "guard-guide.md", "scorecard.md", "records.md", "task-routes.md"];
const knowledgeDocs = manifest?.knowledge?.entries ?? ["README.md", "dev-map.md", "contexts.md", "flows.md"];
const required = [instructionEntry, ".harness/manifest.json", ...coreDocs.map((name) => `${docsRoot}/${name}`)];

if (["standard", "controlled"].includes(tier)) {
  required.push(...standardDocs.map((name) => `${docsRoot}/${name}`));
  required.push(...knowledgeDocs.map((name) => `${knowledgeRoot}/${name}`));
}

for (const relative of required) {
  if (!exists(relative)) fail(`Missing required Harness file: ${relative}`);
}

if (manifest.schemaVersion !== 1) fail(".harness/manifest.json schemaVersion must be 1");
if (!["core", "standard", "controlled"].includes(tier)) fail(`Unsupported Harness tier: ${tier}`);
if (!manifest?.project?.name || !manifest?.project?.baseBranch || !manifest?.project?.primaryLanguages?.length) {
  fail(".harness/manifest.json must declare project name, baseBranch, and primaryLanguages");
}
for (const key of ["noTouch", "generated", "externalConsumers"]) {
  if (!Array.isArray(manifest?.boundaries?.[key])) fail(`boundaries.${key} must be an array`);
}
if (!manifest.commands || Object.keys(manifest.commands).length === 0) {
  fail(".harness/manifest.json must declare at least one command");
}

for (const [index, item] of (manifest.toolchain ?? []).entries()) {
  if (!allowedStatuses.has(item.status)) fail(`toolchain[${index}] has an invalid evidence status`);
  if (item.source && !exists(item.source)) warn(`Toolchain source does not exist: ${item.source}`);
}

for (const [name, item] of Object.entries(manifest.commands ?? {})) {
  if (!item.command || !item.source) fail(`commands.${name} must declare command and source`);
  if (!allowedStatuses.has(item.status)) fail(`commands.${name} has an invalid evidence status`);
  if (item.source && !/^https?:\/\//.test(item.source) && !exists(item.source.split("#", 1)[0])) {
    fail(`commands.${name} source does not exist: ${item.source}`);
  }
}

const policyDocs = [
  "CONTRIBUTING.md",
  "README.md",
  "README_zh-CN.md",
  ".github/pull_request_template.md",
  ".github/RELEASE_TEMPLATE.md",
  ".github/ISSUE_REPLY_TEMPLATE.md",
  "docs/maintainers/development-harness.md",
  "docs/maintainers/versioning.md",
  "docs/maintainers/release-and-issue-replies.md",
  "docs/i18n/CONTRIBUTING_zh-CN.md",
  "docs/i18n/glossary_zh-CN.md",
];
const markdownFiles = [...new Set([...required, ...policyDocs])]
  .filter((relative) => relative.endsWith(".md") && exists(relative));
const linkPattern = /(?<!!)\[[^\]]+\]\(([^)]+)\)/g;
const placeholderPattern = /\{\{[^}]+\}\}|\[(?:TODO|TBD)[^\]]*\]|\b(?:TODO|TBD):/i;
const personalPathPattern = /\/Users\/[^/\s]+\/|\/home\/[^/\s]+\/|[A-Za-z]:\\Users\\[^\\\s]+\\/;
const secretPatterns = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /\bghp_[A-Za-z0-9]{20,}\b/,
  /\bgithub_pat_[A-Za-z0-9_]{20,}\b/,
  /\bAKIA[0-9A-Z]{16}\b/,
  /(?:token|secret|password|authorization|cookie)\s*[:=]\s*["']?[^\s'"<]{8,}/i,
];
const placeholderAllowed = new Set([
  ".github/RELEASE_TEMPLATE.md",
  ".github/ISSUE_REPLY_TEMPLATE.md",
  "docs/maintainers/release-and-issue-replies.md",
]);

for (const relative of markdownFiles) {
  const absolute = path.join(root, relative);
  const text = fs.readFileSync(absolute, "utf8");
  if (!placeholderAllowed.has(relative) && placeholderPattern.test(text)) {
    fail(`Unresolved placeholder in ${relative}`);
  }
  if (personalPathPattern.test(text)) fail(`Machine-specific path in ${relative}`);
  if (secretPatterns.some((pattern) => pattern.test(text))) fail(`Credential-like literal in ${relative}`);

  for (const match of text.matchAll(linkPattern)) {
    const raw = match[1].trim().split(/\s+/, 1)[0].replace(/^<|>$/g, "");
    if (!raw || raw.includes("{{") || /^(#|https?:\/\/|mailto:|data:)/.test(raw)) continue;
    const target = decodeURIComponent(raw.split("#", 1)[0].split("?", 1)[0]);
    if (!target) continue;
    const resolved = path.resolve(path.dirname(absolute), target);
    if (!resolved.startsWith(`${root}${path.sep}`) && resolved !== root) {
      fail(`Link escapes the repository in ${relative}: ${raw}`);
    } else if (!fs.existsSync(resolved)) {
      fail(`Broken local link in ${relative}: ${raw}`);
    }
  }
}

if (exists(instructionEntry)) {
  const lines = fs.readFileSync(path.join(root, instructionEntry), "utf8").split(/\r?\n/).length;
  if (lines > 150) warn(`${instructionEntry} has ${lines} lines; keep the entry map at or below 150`);
  if (!fs.readFileSync(path.join(root, instructionEntry), "utf8").includes(docsRoot)) {
    fail(`${instructionEntry} does not route readers to ${docsRoot}`);
  }
}

for (const message of warnings) console.warn(`WARN: ${message}`);
for (const message of errors) console.error(`ERROR: ${message}`);

if (errors.length > 0) {
  console.error(`Harness check failed with ${errors.length} error(s) and ${warnings.length} warning(s).`);
  process.exit(1);
}

console.log(`Harness check passed (${required.length} required files, ${markdownFiles.length} Markdown files, ${warnings.length} warning(s)).`);
