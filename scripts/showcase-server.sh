#!/usr/bin/env bash
# Starts the showcase setup for the README and website screenshots (issue #741): Navidrome with a made-up library
# (scripts/showcase), a listening past on it, stand-ins for AudioMuse-AI and an AI service, and Audiobookshelf with
# made-up books and podcasts.
#   Navidrome: http://127.0.0.1:4533 (10.0.2.2:4533 from the emulator)
#   AudioMuse and AI stand-ins: http://127.0.0.1:8000 (10.0.2.2:8000), AI at /v1
#   Audiobookshelf: http://127.0.0.1:13378 (10.0.2.2:13378), admin/admin
# Needs ffmpeg, Python 3 with Pillow and numpy, the Inter font (fonts-inter) for the covers' lettering, and Docker.
set -euo pipefail
ND_VERSION="${ND_VERSION:-0.64.2}"
# Audiobookshelf's own image, at a version a month old.
ABS_IMAGE="${ABS_IMAGE:-ghcr.io/advplyr/audiobookshelf:2.37.1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="${RUNNER_TEMP:-/tmp}/showcase"
mkdir -p "$DIR" && cd "$DIR"

if [ ! -x navidrome ]; then
  curl -sSL -o nd.tgz "https://github.com/navidrome/navidrome/releases/download/v${ND_VERSION}/navidrome_${ND_VERSION}_linux_amd64.tar.gz"
  tar xzf nd.tgz navidrome
fi
[ -d music ] || python3 "$HERE/showcase/library.py" "$DIR/music"

# No outside services: Navidrome mustn't fetch real artists' photos or biographies for the made-up names.
ND_MUSICFOLDER="$DIR/music" ND_DATAFOLDER="$DIR/data" ND_PORT=4533 ND_ADDRESS=0.0.0.0 ND_SCANNER_SCHEDULE=0 \
  ND_ENABLEEXTERNALSERVICES=false ND_LASTFM_ENABLED=false ND_DEEZER_ENABLED=false ND_LOGLEVEL=info \
  nohup ./navidrome > "$DIR/navidrome.log" 2>&1 &
for i in $(seq 1 30); do curl -s localhost:4533/ping >/dev/null && break; sleep 1; done
curl -s -X POST localhost:4533/auth/createAdmin -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' >/dev/null || true
A="u=admin&p=admin&v=1.16.1&c=showcase&f=json"
curl -s "localhost:4533/rest/startScan?$A&fullScan=true" >/dev/null
for i in $(seq 1 90); do
  sleep 2
  curl -s "localhost:4533/rest/getScanStatus?$A" | grep -q '"scanning":false' && break
done
curl -s "localhost:4533/rest/getScanStatus?$A"; echo
python3 "$HERE/showcase/seed.py" http://127.0.0.1:4533

nohup python3 "$HERE/showcase/fake_services.py" 8000 http://127.0.0.1:4533 > "$DIR/services.log" 2>&1 &
for i in $(seq 1 20); do curl -s localhost:8000/api/health >/dev/null && break; sleep 0.5; done
curl -s -X POST localhost:8000/api/clap/search -d '{"query":"warm soulful evening","limit":3}'; echo

# Audiobookshelf, its books and podcasts made up (books.py), set up by its API (abs_seed.py).
[ -d books ] || python3 "$HERE/showcase/books.py" "$DIR/books" "$DIR/podcasts"
mkdir -p "$DIR/abs-config" "$DIR/abs-metadata"
docker pull -q "$ABS_IMAGE"
docker run -d --name showcase-abs -p 13378:80 -e TZ=UTC \
  -v "$DIR/books:/audiobooks" -v "$DIR/podcasts:/podcasts" \
  -v "$DIR/abs-config:/config" -v "$DIR/abs-metadata:/metadata" "$ABS_IMAGE" >/dev/null
python3 "$HERE/showcase/abs_seed.py" http://127.0.0.1:13378
