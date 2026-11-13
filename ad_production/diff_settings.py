import subprocess

res = subprocess.run(["git", "diff", "app/src/main/java/app/memora/presentation/settings/SettingsScreen.kt"], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if ("AI" in line or "smart scan" in line or "unlimited" in line) and (line.startswith("+") or line.startswith("-")):
        print(line)
