import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const requestedRoot = path.resolve(process.argv[2] ?? defaultRoot);

let root;
try {
  root = fs.realpathSync.native(requestedRoot);
} catch {
  console.error("ERROR: Repository root cannot be resolved.");
  process.exit(1);
}

const errors = [];
const warnings = [];

const fail = (message) => errors.push(message);
const warn = (message) => warnings.push(message);
const isInsideRoot = (absolute) => absolute === root || absolute.startsWith(`${root}${path.sep}`);
const resolveRepositoryPath = (relative, label) => {
  if (typeof relative !== "string" || relative.length === 0 || relative.includes("\0") || path.isAbsolute(relative)) {
    fail(`${label} must be a non-empty repository-relative path.`);
    return null;
  }

  const absolute = path.resolve(root, relative);
  if (!isInsideRoot(absolute)) {
    fail(`${label} must stay inside the repository root.`);
    return null;
  }

  if (fs.existsSync(absolute)) {
    let canonical;
    try {
      canonical = fs.realpathSync.native(absolute);
    } catch {
      fail(`${label} cannot be resolved safely.`);
      return null;
    }
    if (!isInsideRoot(canonical)) {
      fail(`${label} must not resolve through a link outside the repository root.`);
      return null;
    }
    return canonical;
  }

  return absolute;
};

const manifestPath = resolveRepositoryPath(".harness/manifest.json", "Harness manifest");

let manifest;
try {
  if (!manifestPath) throw new Error("unsafe manifest path");
  manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
} catch (error) {
  fail(`Cannot parse .harness/manifest.json: ${error instanceof SyntaxError ? error.message : "file is unavailable"}`);
  manifest = {};
}

const allowedStatuses = new Set(["observed", "declared", "inferred", "unknown"]);
const tier = manifest?.harness?.tier ?? "core";
const docsRoot = manifest?.harness?.docsRoot ?? "docs/harness";
const instructionEntry = manifest?.harness?.instructionEntry ?? "AGENTS.md";
const knowledgeRoot = manifest?.knowledge?.root ?? "docs/project-knowledge";

const coreDocs = ["README.md", "change-protocol.md", "testing-guide.md", "templates.md"];
const standardDocs = ["quality-rules.md", "guard-guide.md", "scorecard.md", "records.md", "task-routes.md"];
const configuredKnowledgeDocs = manifest?.knowledge?.entries;
const knowledgeDocs = configuredKnowledgeDocs == null
  ? ["README.md", "dev-map.md", "contexts.md", "flows.md"]
  : Array.isArray(configuredKnowledgeDocs)
    ? configuredKnowledgeDocs
    : (fail("knowledge.entries must be an array"), []);
const required = [instructionEntry, ".harness/manifest.json", ...coreDocs.map((name) => `${docsRoot}/${name}`)];

if (["standard", "controlled"].includes(tier)) {
  required.push(...standardDocs.map((name) => `${docsRoot}/${name}`));
  required.push(...knowledgeDocs.map((name) => `${knowledgeRoot}/${name}`));
}

for (const relative of required) {
  const absolute = resolveRepositoryPath(relative, "Required Harness file");
  if (absolute && !fs.existsSync(absolute)) fail(`Missing required Harness file: ${relative}`);
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
  if (item.source) {
    const absolute = resolveRepositoryPath(item.source, `toolchain[${index}] source`);
    if (absolute && !fs.existsSync(absolute)) warn(`Toolchain source does not exist: ${item.source}`);
  }
}

for (const [name, item] of Object.entries(manifest.commands ?? {})) {
  if (!item.command || !item.source) fail(`commands.${name} must declare command and source`);
  if (!allowedStatuses.has(item.status)) fail(`commands.${name} has an invalid evidence status`);
  if (item.source && !/^https?:\/\//.test(item.source)) {
    const source = item.source.split("#", 1)[0];
    const absolute = resolveRepositoryPath(source, `commands.${name} source`);
    if (absolute && !fs.existsSync(absolute)) fail(`commands.${name} source does not exist: ${item.source}`);
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
  .filter((relative) => typeof relative === "string" && relative.endsWith(".md"))
  .map((relative) => ({
    relative,
    absolute: resolveRepositoryPath(relative, "Markdown file"),
  }))
  .filter(({ absolute }) => absolute && fs.existsSync(absolute));
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

for (const { relative, absolute } of markdownFiles) {
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
    const safeTarget = resolveRepositoryPath(path.relative(root, resolved) || ".", `Local link in ${relative}`);
    if (!safeTarget) continue;
    if (!fs.existsSync(safeTarget)) {
      fail(`Broken local link in ${relative}: ${raw}`);
    }
  }
}

const instructionPath = resolveRepositoryPath(instructionEntry, "Harness instruction entry");
if (instructionPath && fs.existsSync(instructionPath)) {
  const instructionText = fs.readFileSync(instructionPath, "utf8");
  const lines = instructionText.split(/\r?\n/).length;
  if (lines > 150) warn(`${instructionEntry} has ${lines} lines; keep the entry map at or below 150`);
  if (!instructionText.includes(docsRoot)) {
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
