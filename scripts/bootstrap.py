"""Fetch the reviewed whisper.cpp revision. Does not install host software."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "third_party" / "whisper.cpp"
REVISION = "48f628a84833905ee4a0658ee6d4a5c915ce1997"  # v1.8.7

def git(*args):
    return subprocess.run(["git", *map(str, args)], check=True, text=True, capture_output=True).stdout.strip()

if DEST.exists():
    if not (DEST / ".git").exists() or git("-C", DEST, "rev-parse", "HEAD") != REVISION:
        sys.exit("Existing third_party/whisper.cpp is not the expected checkout; preserve it and resolve manually.")
    if git("-C", DEST, "status", "--porcelain"):
        sys.exit("whisper.cpp has local changes; restore/review them before building.")
else:
    DEST.mkdir(parents=True)
    git("init", DEST)
    git("-C", DEST, "remote", "add", "origin", "https://github.com/ggml-org/whisper.cpp.git")
    git("-C", DEST, "fetch", "--depth=1", "origin", REVISION)
    git("-C", DEST, "checkout", "--detach", "FETCH_HEAD")
assert git("-C", DEST, "rev-parse", "HEAD") == REVISION
print(f"whisper.cpp ready: {REVISION}")
