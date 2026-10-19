"""Freeze the candidate SQL baseline; do not silently re-baseline old prototypes."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
manifest = json.loads((root / "config/studio-release-baseline.json").read_text())
for name, expected in manifest["sql"].items():
    actual = hashlib.sha256((root / name).read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f"Frozen Studio baseline changed: {name}; introduce a reviewed migration")
package = json.loads((root / "gear4jtest-studio-web/package.json").read_text())
assert package["version"] == manifest["npmVersion"]
handler = root / "gear4jtest-studio-http/src/main/java/io/github/gear4jtest/studio/http/StudioManagementHttpHandler.java"
assert f'PROTOCOL_VERSION = "{manifest["httpProtocol"]}"' in handler.read_text()
print("Studio candidate baseline and protocol verified")
