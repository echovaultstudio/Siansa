#!/usr/bin/env bash
# Copies Siansa Beats (dev) into a clone of the public plugin repository, on its dev branch, as an AudioMuse-AI
# catalog of its own, so the Siansa plugin's catalog (manifest.json on main) never lists it:
#
#   manifest-dev.json                             the dev catalog (add its raw address in AudioMuse-AI)
#   plugins/SiansaBeats/plugin.json               versions, each with its zip address and checksum
#   plugins/SiansaBeats/siansa_beats/             the source, for reading
#   dist/siansa_beats-<version>.zip               the code AudioMuse-AI downloads
#
#   audiomuse-plugin-beats/publish.sh ../siansa-audiomuse-plugin
#
# A version's zip is never replaced once published (installed copies check its checksum): bump the version in
# plugin.json (a new entry at the top of "versions") for every change.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
target="$(cd "${1:?give the clone of the public plugin repository, on its dev branch}" && pwd)"
raw="${2:-https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/dev}"
raw="${raw%/}"
python3 - "$here" "$target" "$raw" <<'PY'
import hashlib, io, json, os, shutil, sys, zipfile
here, target, raw = sys.argv[1:]
plugin = json.load(open(os.path.join(here, "plugin.json")))
source = os.path.join(here, "siansa_beats")
files = sorted(f for f in os.listdir(source) if f.endswith(".py"))

buffer = io.BytesIO()
with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
    for name in files:
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, open(os.path.join(source, name), "rb").read())
data = buffer.getvalue()

published_path = os.path.join(target, "plugins", "SiansaBeats", "plugin.json")
published = {}
if os.path.exists(published_path):
    published = {v["version"]: v for v in json.load(open(published_path)).get("versions", [])}
newest = plugin["versions"][0]["version"]
zip_name = f"siansa_beats-{newest}.zip"
checksum = hashlib.md5(data).hexdigest()
if newest in published and published[newest].get("checksum") != checksum:
    sys.exit(f"Version {newest} is already published with other code. Add a new version to plugin.json first.")
os.makedirs(os.path.join(target, "dist"), exist_ok=True)
with open(os.path.join(target, "dist", zip_name), "wb") as f:
    f.write(data)

versions = []
for v in plugin["versions"]:
    if v["version"] == newest:
        versions.append({**v, "sourceUrl": f"{raw}/dist/{zip_name}", "checksum": checksum})
    elif v["version"] in published:
        versions.append(published[v["version"]])
os.makedirs(os.path.dirname(published_path), exist_ok=True)
with open(published_path, "w") as f:
    json.dump({**plugin, "versions": versions}, f, indent=2)
    f.write("\n")

code = os.path.join(target, "plugins", "SiansaBeats", "siansa_beats")
shutil.rmtree(code, ignore_errors=True)
os.makedirs(code)
for name in files:
    shutil.copy(os.path.join(source, name), code)

manifest = {"plugins": [{k: plugin[k] for k in ("id", "name", "author", "description")}
                        | {"pluginUrl": f"{raw}/plugins/SiansaBeats/plugin.json"}]}
with open(os.path.join(target, "manifest-dev.json"), "w") as f:
    json.dump(manifest, f, indent=2)
    f.write("\n")
print(f"Published {zip_name} ({checksum}): add {raw}/manifest-dev.json in AudioMuse-AI")
PY
