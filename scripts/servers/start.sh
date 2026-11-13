#!/usr/bin/env bash
# Starts Navidrome, Emby and Jellyfin with the same test library (scripts/servers/library.py), for the tests that run
# Siansa's own server code against real servers (app/src/test/java/app/siansa/servers/, issue #903).
#   Navidrome http://127.0.0.1:4533 · Emby http://127.0.0.1:8096 · Jellyfin http://127.0.0.1:8097 · user max, password sesame
# Navidrome runs from its release; Emby and Jellyfin in Docker. Versions can be set with ND_VERSION, EMBY_VERSION
# and JELLYFIN_VERSION (a release, or "latest"; Emby also "beta"). Writes servers.env (what the tests need) next to the library.
set -euo pipefail
ND_VERSION="${ND_VERSION:-0.64.2}"
EMBY_VERSION="${EMBY_VERSION:-4.8.10.0}"
JELLYFIN_VERSION="${JELLYFIN_VERSION:-10.10.7}"
# "latest" asks GitHub for Navidrome's newest release: with the runner's token when there is one (shared runners
# run into the anonymous rate limit: "KeyError: 'tag_name'"), and the pinned version when GitHub doesn't answer.
if [ "$ND_VERSION" = latest ]; then
  auth=(); [ -n "${GITHUB_TOKEN:-}" ] && auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  newest="$(curl -sSL "${auth[@]}" https://api.github.com/repos/navidrome/navidrome/releases/latest \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tag_name", "").lstrip("v"))' 2>/dev/null || true)"
  if [ -n "$newest" ]; then ND_VERSION="$newest"; else ND_VERSION=0.64.2; echo "GitHub didn't say Navidrome's newest release: using $ND_VERSION"; fi
fi
echo "Navidrome $ND_VERSION, Emby $EMBY_VERSION, Jellyfin $JELLYFIN_VERSION"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="${RUNNER_TEMP:-/tmp}/siansa-servers"
mkdir -p "$DIR" && cd "$DIR"

[ -f music/library.json ] || python3 "$HERE/library.py" "$DIR/music"

# Emby and Jellyfin start downloading at once: they take the longest.
docker pull -q "emby/embyserver:$EMBY_VERSION" > /dev/null &
docker pull -q "jellyfin/jellyfin:$JELLYFIN_VERSION" > /dev/null &
if [ ! -x navidrome ]; then
  curl -sSL -o nd.tgz "https://github.com/navidrome/navidrome/releases/download/v${ND_VERSION}/navidrome_${ND_VERSION}_linux_amd64.tar.gz"
  tar xzf nd.tgz navidrome
fi
wait

mkdir -p nd-data emby-config jellyfin-config jellyfin-cache
ND_MUSICFOLDER="$DIR/music" ND_DATAFOLDER="$DIR/nd-data" ND_PORT=4533 ND_ADDRESS=127.0.0.1 ND_SCANNER_SCHEDULE=0 \
  ND_ENABLEEXTERNALSERVICES=false ND_LOGLEVEL=info nohup ./navidrome > "$DIR/navidrome.log" 2>&1 &
docker run -d --name siansa-emby -p 8096:8096 -e UID="$(id -u)" -e GID="$(id -g)" \
  -v "$DIR/emby-config:/config" -v "$DIR/music:/music:ro" "emby/embyserver:$EMBY_VERSION" > /dev/null
docker run -d --name siansa-jellyfin -p 8097:8096 --user "$(id -u):$(id -g)" \
  -v "$DIR/jellyfin-config:/config" -v "$DIR/jellyfin-cache:/cache" -v "$DIR/music:/music:ro" "jellyfin/jellyfin:$JELLYFIN_VERSION" > /dev/null

python3 "$HERE/setup.py" "$DIR/music/library.json"

cat > "$DIR/servers.env" <<ENV
SIANSA_TEST_SERVERS=navidrome=http://127.0.0.1:4533,emby=http://127.0.0.1:8096,jellyfin=http://127.0.0.1:8097
SIANSA_TEST_LIBRARY=$DIR/music/library.json
ENV
cat "$DIR/servers.env"
