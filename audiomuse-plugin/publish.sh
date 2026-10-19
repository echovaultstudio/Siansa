#!/usr/bin/env bash
# Copies the plugin into a clone of the public plugin repository, laid out as an AudioMuse-AI catalog:
#
#   manifest.json                                    the catalog (add its raw address in AudioMuse-AI)
#   plugins/MusixAnalysis/plugin.json                versions, each with its zip address and checksum
#   plugins/MusixAnalysis/musix_analysis/            the source, for reading
#   dist/musix_analysis-<version>.zip                the code AudioMuse-AI downloads
#
#   audiomuse-plugin/publish.sh ../musix-audiomuse-plugin [https://raw.githubusercontent.com/<owner>/<repo>/main]
#
# A version's zip is never replaced once published (installed copies check its checksum): bump the version
# in plugin.json (a new entry at the top of "versions") for every change.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
target="$(cd "${1:?give the clone of the public plugin repository}" && pwd)"
raw="${2:-https://raw.githubusercontent.com/Th3K1ngP1n/Musix-Industreez-musix-audiomuse-plugin/main}"
raw="${raw%/}"
python3 - "$here" "$target" "$raw" <<'PY'
import hashlib, io, json, os, shutil, sys, zipfile
here, target, raw = sys.argv[1:]
plugin = json.load(open(os.path.join(here, "plugin.json")))
source = os.path.join(here, "musix_analysis")
files = sorted(f for f in os.listdir(source) if f.endswith(".py"))

# The zip, byte for byte the same for the same code (fixed dates), so rebuilding is harmless.
buffer = io.BytesIO()
with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
    for name in files:
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, open(os.path.join(source, name), "rb").read())
data = buffer.getvalue()

# Versions already published keep their entries (address and checksum) exactly.
published_path = os.path.join(target, "plugins", "MusixAnalysis", "plugin.json")
published = {}
if os.path.exists(published_path):
    published = {v["version"]: v for v in json.load(open(published_path)).get("versions", [])}

newest = plugin["versions"][0]["version"]
zip_name = f"musix_analysis-{newest}.zip"
zip_path = os.path.join(target, "dist", zip_name)
checksum = hashlib.md5(data).hexdigest()
if newest in published and published[newest].get("checksum") != checksum:
    sys.exit(f"Version {newest} is already published with other code. Add a new version to plugin.json first.")
os.makedirs(os.path.dirname(zip_path), exist_ok=True)
with open(zip_path, "wb") as f:
    f.write(data)

versions = []
for v in plugin["versions"]:
    if v["version"] == newest:
        versions.append({**v, "sourceUrl": f"{raw}/dist/{zip_name}", "checksum": checksum})
    elif v["version"] in published:
        versions.append(published[v["version"]])
out = {**plugin, "versions": versions}
os.makedirs(os.path.dirname(published_path), exist_ok=True)
json.dump(out, open(published_path, "w"), indent=2)
open(published_path, "a").write("\n")

code = os.path.join(target, "plugins", "MusixAnalysis", "musix_analysis")
shutil.rmtree(code, ignore_errors=True)
os.makedirs(code)
for name in files:
    shutil.copy(os.path.join(source, name), code)
shutil.copy(os.path.join(here, "README.md"), os.path.join(target, "plugins", "MusixAnalysis", "README.md"))

manifest = {"plugins": [{k: plugin[k] for k in ("id", "name", "author", "description")}
                        | {"pluginUrl": f"{raw}/plugins/MusixAnalysis/plugin.json"}]}
json.dump(manifest, open(os.path.join(target, "manifest.json"), "w"), indent=2)
open(os.path.join(target, "manifest.json"), "a").write("\n")
print(f"Published {newest}: {zip_name} ({checksum})")
PY
