import os
import shutil

ROOT_DIR = r"D:\softwares\SearXNG\PikachuBrowser"

REPLACEMENTS = [
    ("com.example.pikachubrowser", "com.example.peachbrowser"),
    ("PikachuBrowser", "PeachBrowser"),
    ("pikachubrowser", "peachbrowser"),
    ("PikachuApplication", "PeachApplication"),
    ("PikachuMediaService", "PeachMediaService"),
    ("PikachuWebView", "PeachWebView"),
    ("PikachuWebViewController", "PeachWebViewController"),
    ("rememberPikachuWebViewController", "rememberPeachWebViewController"),
    ("PikachuNativeWebView", "PeachNativeWebView"),
    ("PikachuDatabase", "PeachDatabase"),
    ("pikachu_media_playback", "peach_media_playback"),
    ("Pikachu", "Peach"),
    ("pikachu", "peach"),
]

# File extensions to replace text in
TEXT_EXTS = {".kt", ".kts", ".xml", ".sq", ".properties", ".gradle", ".md", ".json", ".js", ".html"}

print("Step 1: Text replacement across files...")
for root, dirs, files in os.walk(ROOT_DIR):
    if ".git" in root or "build" in root or ".gradle" in root:
        continue
    for file in files:
        ext = os.path.splitext(file)[1].lower()
        if ext in TEXT_EXTS or file in {"gradle.properties", "settings.gradle.kts", "build.gradle.kts"}:
            file_path = os.path.join(root, file)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                new_content = content
                for old_val, new_val in REPLACEMENTS:
                    new_content = new_content.replace(old_val, new_val)
                
                if new_content != content:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    print(f"Updated text: {file_path}")
            except Exception as e:
                print(f"Error reading/writing {file_path}: {e}")

print("Step 2: Renaming individual files...")
for root, dirs, files in os.walk(ROOT_DIR, topdown=False):
    if ".git" in root or "build" in root or ".gradle" in root:
        continue
    for file in files:
        if "Pikachu" in file or "pikachu" in file:
            new_file_name = file.replace("Pikachu", "Peach").replace("pikachu", "peach")
            old_path = os.path.join(root, file)
            new_path = os.path.join(root, new_file_name)
            if old_path != new_path:
                if os.path.exists(new_path):
                    os.remove(old_path)
                    print(f"Removed duplicate old file: {old_path}")
                else:
                    os.rename(old_path, new_path)
                    print(f"Renamed file: {old_path} -> {new_file_name}")

print("Step 3: Renaming directories (com/example/pikachubrowser -> com/example/peachbrowser)...")
for root, dirs, files in os.walk(ROOT_DIR, topdown=False):
    if ".git" in root or "build" in root or ".gradle" in root:
        continue
    for d in dirs:
        if d == "pikachubrowser":
            old_dir = os.path.join(root, d)
            new_dir = os.path.join(root, "peachbrowser")
            if os.path.exists(new_dir):
                for item in os.listdir(old_dir):
                    s = os.path.join(old_dir, item)
                    dest = os.path.join(new_dir, item)
                    shutil.move(s, dest)
                os.rmdir(old_dir)
            else:
                os.rename(old_dir, new_dir)
            print(f"Renamed dir: {old_dir} -> peachbrowser")

print("Refactoring inside PikachuBrowser directory complete!")
