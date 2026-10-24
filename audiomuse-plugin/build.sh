#!/usr/bin/env bash
# Builds the plugin into dist/ as AudioMuse-AI installs it: the code zip, plugin.json with the zip's address
# and checksum, and a one-plugin catalog (manifest.json).
#
#   audiomuse-plugin/build.sh http://192.168.1.20:8000
#
# The address is where dist/ will be served from, reachable by the AudioMuse-AI containers. Then serve it
# (cd audiomuse-plugin/dist && python3 -m http.server 8000) and add <address>/manifest.json under
# Plugins > Repositories in AudioMuse-AI.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
base="${1:?give the address dist/ will be served from, e.g. http://192.168.1.20:8000}"
base="${base%/}"
dist="$here/dist"
rm -rf "$dist" && mkdir -p "$dist"
version="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versions"][0]["version"])' "$here/plugin.json")"
zip_name="siansa_analysis-$version.zip"
(cd "$here/siansa_analysis" && find . -name '__pycache__' -prune -o -type f -name '*.py' -print | sort | zip -q -X "$dist/$zip_name" -@)
python3 - "$here/plugin.json" "$dist" "$base" "$zip_name" <<'PY'
import hashlib, json, os, sys
source, dist, base, zip_name = sys.argv[1:]
plugin = json.load(open(source))
checksum = hashlib.md5(open(os.path.join(dist, zip_name), "rb").read()).hexdigest()
plugin["versions"][0].update(sourceUrl=f"{base}/{zip_name}", checksum=checksum)
json.dump(plugin, open(os.path.join(dist, "plugin.json"), "w"), indent=2)
manifest = {"plugins": [{k: plugin[k] for k in ("id", "name", "author", "description")} | {"pluginUrl": f"{base}/plugin.json"}]}
json.dump(manifest, open(os.path.join(dist, "manifest.json"), "w"), indent=2)
print(f"Built {zip_name} ({checksum}) in {dist}")
PY
