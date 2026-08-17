import subprocess
import time

ADB = r"C:\Users\barba\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def adb_cmd(*args):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True)

# Clear logcat
adb_cmd("logcat", "-c")
time.sleep(0.2)

print("Tapping Register at (780, 740)...")
adb_cmd("shell", "input", "tap", "780", "740")
time.sleep(4)

adb_cmd("shell", "screencap", "-p", "/sdcard/release_after_click.png")
adb_cmd("pull", "/sdcard/release_after_click.png", r"C:\Users\barba\AppData\Local\hermes\kanban\boards\fitness-app\workspaces\t_7d0cdebb\release_after_click.png")

