import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const checker = path.join(repositoryRoot, "scripts", "check-harness.mjs");
const temporaryRoots = [];

const write = (root, relative, contents) => {
  const absolute = path.join(root, relative);
  fs.mkdirSync(path.dirname(absolute), { recursive: true });
  fs.writeFileSync(absolute, contents);
};

const createFixture = () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "komelia-harness-check-"));
  temporaryRoots.push(root);
  const manifest = {
    schemaVersion: 1,
    project: { name: "fixture", baseBranch: "main", primaryLanguages: ["JavaScript"] },
    toolchain: [],
    commands: {
      harnessCheck: {
        command: "node scripts/check-harness.mjs",
        source: "scripts/check-harness.mjs",
        status: "declared",
      },
    },
    boundaries: { noTouch: [], generated: [], externalConsumers: [] },
    harness: { tier: "core", docsRoot: "docs/harness", instructionEntry: "AGENTS.md" },
  };

  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);
  write(root, "AGENTS.md", "Read docs/harness before changing this fixture.\n");
  write(root, "scripts/check-harness.mjs", "// fixture command source\n");
  write(root, "docs/harness/README.md", "# README\n\n[Repository root](../..)\n");
  for (const name of ["change-protocol.md", "testing-guide.md", "templates.md"]) {
    write(root, `docs/harness/${name}`, `# ${name}\n`);
  }
  return { root, manifest };
};

const runChecker = (root) => spawnSync(process.execPath, [checker, root], { encoding: "utf8" });

test.after(() => {
  for (const root of temporaryRoots) fs.rmSync(root, { recursive: true, force: true });
});

test("accepts a valid repository-local Harness", () => {
  const { root } = createFixture();
  const result = runChecker(root);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Harness check passed/);
});

test("rejects parent traversal without reading or echoing outside Markdown", () => {
  const { root, manifest } = createFixture();
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), "komelia-harness-private-"));
  temporaryRoots.push(outside);
  const sentinel = "private-link-target-that-must-not-appear";
  write(outside, "README.md", `[private](${sentinel})\n`);
  manifest.harness.instructionEntry = path.relative(root, path.join(outside, "README.md"));
  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);

  const result = runChecker(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /must stay inside the repository root/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, new RegExp(sentinel));
});

test("rejects an absolute manifest path", () => {
  const { root, manifest } = createFixture();
  manifest.harness.instructionEntry = path.join(root, "AGENTS.md");
  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);

  const result = runChecker(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /must be a non-empty repository-relative path/);
});

test("rejects a sibling path that only shares the repository name prefix", () => {
  const { root, manifest } = createFixture();
  const sibling = `${root}-outside`;
  temporaryRoots.push(sibling);
  write(sibling, "AGENTS.md", "outside\n");
  manifest.harness.instructionEntry = path.relative(root, path.join(sibling, "AGENTS.md"));
  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);

  const result = runChecker(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /must stay inside the repository root/);
});

test("rejects a repository symlink that resolves outside the root", (context) => {
  const { root, manifest } = createFixture();
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), "komelia-harness-linked-"));
  temporaryRoots.push(outside);
  write(outside, "private.md", "outside\n");
  try {
    fs.symlinkSync(path.join(outside, "private.md"), path.join(root, "linked.md"));
  } catch (error) {
    if (["EPERM", "EACCES", "ENOTSUP"].includes(error.code)) {
      context.skip(`Symlinks are unavailable: ${error.code}`);
      return;
    }
    throw error;
  }
  manifest.harness.instructionEntry = "linked.md";
  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);

  const result = runChecker(root);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /must not resolve through a link outside the repository root/);
});

test("accepts a repository symlink that resolves inside the root", (context) => {
  const { root, manifest } = createFixture();
  write(root, "docs/instructions.md", "Read docs/harness before changing this fixture.\n");
  try {
    fs.symlinkSync(path.join(root, "docs", "instructions.md"), path.join(root, "linked.md"));
  } catch (error) {
    if (["EPERM", "EACCES", "ENOTSUP"].includes(error.code)) {
      context.skip(`Symlinks are unavailable: ${error.code}`);
      return;
    }
    throw error;
  }
  manifest.harness.instructionEntry = "linked.md";
  write(root, ".harness/manifest.json", `${JSON.stringify(manifest, null, 2)}\n`);

  const result = runChecker(root);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Harness check passed/);
});
