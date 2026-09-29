"""Set cmake.dir for pip cmake and copy pip Ninja into CMake's bin, as AGP expects."""
from pathlib import Path
import shutil
import sys
import cmake

root = Path(__file__).resolve().parent.parent
cmake_home = Path(cmake.CMAKE_BIN_DIR).parent
ninja_name = "ninja.exe" if sys.platform == "win32" else "ninja"
ninja = shutil.which(ninja_name) or str(Path(sys.executable).parent / ninja_name)
target = cmake_home / "bin" / ninja_name
if Path(ninja).resolve() != target.resolve():
    shutil.copy2(ninja, target)
properties = root / "local.properties"
lines = properties.read_text(encoding="utf-8").splitlines() if properties.exists() else []
lines = [line for line in lines if not line.startswith("cmake.dir=")]
lines.append("cmake.dir=" + cmake_home.as_posix().replace(":", "\\:"))
properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
print("Configured pip CMake and Ninja in local.properties.")
