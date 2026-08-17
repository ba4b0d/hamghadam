import subprocess
import time

ADB = r"C:\Users\barba\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def adb_cmd(*args):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True)

# 1. Focus email
adb_cmd("shell", "input", "tap", "540", "280")
time.sleep(0.5)
# Select all and delete
for _ in range(60):
    adb_cmd("shell", "input", "keyevent", "67")
time.sleep(0.2)

# Type email
adb_cmd("shell", "input", "text", "release_test@hamghadam.ir")
time.sleep(0.5)

# Press TAB to move focus to Password field!
adb_cmd("shell", "input", "keyevent", "61")
time.sleep(0.5)

# Type password
adb_cmd("shell", "input", "text", "TestPass123")
time.sleep(0.5)

# Hide keyboard
adb_cmd("shell", "input", "keyevent", "111")
time.sleep(0.5)

# Take screenshot to verify password box is filled!
adb_cmd("shell", "screencap", "-p", "/sdcard/release_login_filled.png")
adb_cmd("pull", "/sdcard/release_login_filled.png", r"C:\Users\barba\AppData\Local\hermes\kanban\boards\fitness-app\workspaces\t_7d0cdebb\release_login_filled.png")

# Clear logcat
adb_cmd("logcat", "-c")
time.sleep(0.2)

# Tap Sign In button (280, 520)
adb_cmd("shell", "input", "tap", "280", "520")
time.sleep(3)

# Take screenshot after sign in
adb_cmd("shell", "screencap", "-p", "/sdcard/release_login_tapped.png")
adb_cmd("pull", "/sdcard/release_login_tapped.png", r"C:\Users\barba\AppData\Local\hermes\kanban\boards\fitness-app\workspaces\t_7d0cdebb\release_login_tapped.png")

