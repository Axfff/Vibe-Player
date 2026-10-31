"""Safety check before tests that reset the isolated debug application's preferences."""
import os
import subprocess

serial = os.environ.get("ANDROID_SERIAL", "")
if not serial.startswith("emulator-") or os.environ.get("DISPOSABLE_EMULATOR") != "1":
    raise SystemExit("Set ANDROID_SERIAL to a disposable emulator and DISPOSABLE_EMULATOR=1; never use a personal phone")
result = subprocess.check_output(["adb", "-s", serial, "shell", "getprop", "ro.kernel.qemu"], text=True).strip()
if result != "1":
    raise SystemExit("Refusing regression reset: the target is not an emulator")
print("Verified disposable emulator:", serial)
