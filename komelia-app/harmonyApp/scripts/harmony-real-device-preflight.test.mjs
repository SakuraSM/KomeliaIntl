import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const script = fileURLToPath(new URL('./harmony-real-device-validation.sh', import.meta.url));

// A read-only fake transport. Any attempted install/test/signing is an error.
// These tests validate the host preflight, never physical-device behavior.
function preflight(model, api = '20', target = 'usb-fixture') {
  const dir = mkdtempSync(join(tmpdir(), 'komelia-preflight-'));
  try {
    const hdc = join(dir, 'hdc');
    const signTool = join(dir, 'unused-sign-tool.jar');
    writeFileSync(signTool, 'not invoked');
    writeFileSync(hdc, `#!/bin/bash
case "$*" in
  '-t usb-fixture shell param get const.product.model') printf '%s\\n' "$FIXTURE_MODEL" ;;
  '-t usb-fixture shell param get const.ohos.apiversion') printf '%s\\n' "$FIXTURE_API" ;;
  '-t usb-fixture shell param get const.ohos.fullname') printf '%s\\n' 'HarmonyOS fixture' ;;
  *) exit 73 ;;
esac
`, { mode: 0o700 });
    return spawnSync('/bin/bash', [script], {
      encoding: 'utf8',
      env: { ...process.env, HDC: hdc, HDC_TARGET: target, HAP_SIGN_TOOL: signTool,
        HAP_PATH: '', FIXTURE_MODEL: model, FIXTURE_API: api }
    });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('rejects loopback without attempting device operations', () => {
  const result = preflight('fixture', '20', '127.0.0.1:5557');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /rejects emulator target/);
});
test('rejects mixed-case emulator models under system Bash', () => {
  const result = preflight('Test EmULaTor');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /rejects product model/);
});
test('rejects unsupported device API without reaching installation', () => {
  const result = preflight('Phone fixture', '19');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /API 20 or newer is required/);
});
test('accepts the model/API preflight but requires a real signed artifact', () => {
  const result = preflight('Phone fixture');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Set HAP_PATH/);
  assert.doesNotMatch(result.stderr, /bad substitution/);
});
