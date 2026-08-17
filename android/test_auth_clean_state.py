import subprocess
import time

ADB = r"C:\Users\barba\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def adb_cmd(*args):
    res = subprocess.run([ADB] + list(args), capture_output=True, text=True)
    return res.stdout.strip()

print("1. Clearing app data...")
adb_cmd("shell", "pm", "clear", "com.fitnessapp.android")

print("2. Starting MainActivity...")
adb_cmd("shell", "am", "start", "-n", "com.fitnessapp.android/.MainActivity")
time.sleep(2.5)

print("3. Tapping Account tab...")
adb_cmd("shell", "input", "tap", "900", "2280")
time.sleep(1.5)

print("4. Tapping Email field...")
adb_cmd("shell", "input", "tap", "540", "280")
time.sleep(0.5)

print("5. Typing email...")
adb_cmd("shell", "input", "text", "release_user@hamghadam.ir")
time.sleep(0.5)

print("6. Pressing TAB for Password field...")
adb_cmd("shell", "input", "keyevent", "61")
time.sleep(0.5)

print("7. Typing password...")
adb_cmd("shell", "input", "text", "Release123")
time.sleep(0.5)

print("8. Hiding keyboard...")
adb_cmd("shell", "input", "keyevent", "111")
time.sleep(0.8)

print("9. Clearing logcat...")
adb_cmd("logcat", "-c")
time.sleep(0.2)

print("10. Tapping Register button...")
adb_cmd("shell", "input", "tap", "780", "520")
time.sleep(3.5)

print("11. Taking screenshot...")
adb_cmd("shell", "screencap", "-p", "/sdcard/release_register_result.png")
adb_cmd("pull", "/sdcard/release_register_result.png", r"C:\Users\barba\AppData\Local\hermes\kanban\boards\fitness-app\workspaces\t_7d0cdebb\release_register_result.png")

