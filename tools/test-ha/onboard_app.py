#!/usr/bin/env python3
"""Onboard the debug app on a connected device/emulator to the local test instance.

Prerequisites: test instance running (up.sh), debug APK installed, and
`adb reverse tcp:8124 tcp:8124` so the device reaches it as localhost.
Select the device with ANDROID_SERIAL when several are attached.

Always enters the address manually: server discovery can list other (real)
servers on the network, which must never be used.
"""

import re
import subprocess
import sys
import time

PACKAGE = "io.homeassistant.companion.android.minimal.debug"
URL = "http://localhost:8124"
USERNAME = PASSWORD = "dev"


def adb(*args: str) -> str:
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def nodes() -> list[tuple[str, tuple[int, int]]]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    found = []
    for node in re.findall(r"<node [^>]*>", xml):
        label = " ".join(re.findall(r'(?:text|content-desc)="([^"]*)"', node)).replace("\xa0", " ")
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        found.append((label, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return found


def wait_for(text: str, timeout: float = 30) -> tuple[int, int]:
    deadline = time.time() + timeout
    while time.time() < deadline:
        for label, centre in nodes():
            if text.lower() in label.lower():
                return centre
        time.sleep(1.5)
    sys.exit(f"timed out waiting for '{text}'; visible: {[label for label, _ in nodes() if label.strip()]}")


def tap(text: str, timeout: float = 30) -> None:
    x, y = wait_for(text, timeout)
    adb("shell", "input", "tap", str(x), str(y))
    print(f"tapped: {text}")


def no_discovery_sheet() -> None:
    """Close the discovered-server sheet if shown: its Connect button can cover other targets."""
    for _ in range(5):
        labels = [label.strip() for label, _ in nodes()]
        if "Connect" not in labels and "Drag handle" not in labels:
            return
        adb("shell", "input", "keyevent", "4")  # Back closes the sheet
        time.sleep(1.5)
    sys.exit("refused: could not dismiss the discovered-server sheet")


def assert_auth_url_is_local() -> None:
    """Abort unless the app built its login URL for the local test instance."""
    log = adb("logcat", "-d", "-s", "ConnectionViewModel:D")
    urls = re.findall(r"Building auth url based on (\S+)", log)
    if not urls or any(not url.startswith(URL) for url in urls):
        adb("shell", "am", "force-stop", PACKAGE)
        sys.exit(f"refused: login page is not the test instance ({urls}); app stopped, nothing typed")


def main() -> None:
    adb("logcat", "-c")
    # Explicit component: the debug build has a second launcher entry for the native dashboard
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
        "-n", f"{PACKAGE}/io.homeassistant.companion.android.launch.LaunchActivity")
    tap("Connect to my Home Assistant server", timeout=60)
    time.sleep(3)  # let discovery settle so a late sheet cannot slide under the next tap
    no_discovery_sheet()
    tap("Enter address manually")
    x, y = wait_for("homeassistant.local")
    adb("shell", "input", "tap", str(x), str(y))
    adb("shell", "input", "text", URL)
    adb("shell", "input", "keyevent", "66")  # Enter submits the address
    x, y = wait_for("Username", timeout=60)
    assert_auth_url_is_local()
    adb("shell", "input", "tap", str(x), str(y))
    adb("shell", "input", "text", USERNAME)
    x, y = wait_for("Password")
    adb("shell", "input", "tap", str(x), str(y))
    adb("shell", "input", "text", PASSWORD)
    adb("shell", "input", "keyevent", "66")
    tap("Save", timeout=60)
    for optional in ("Got it", "Less secure", "Next", "Do not allow"):
        try:
            tap(optional, timeout=15)
        except SystemExit:
            print(f"skipped: {optional}")
    print("onboarded")


if __name__ == "__main__":
    main()
