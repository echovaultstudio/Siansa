#!/usr/bin/env bash
# Starts a real Navidrome with a small test library for the emulator tests, behind a proxy that limits
# the whole connection to 4.3 Mbit/s (what a phone measured to a home server).
#   Navidrome: http://127.0.0.1:4533   Test link: http://127.0.0.1:5533 (10.0.2.2:5533 from the emulator)
set -euo pipefail
ND_VERSION="${ND_VERSION:-0.64.2}"
DIR="${RUNNER_TEMP:-/tmp}/navidrome-test"
mkdir -p "$DIR" && cd "$DIR"

curl -sSL -o nd.tgz "https://github.com/navidrome/navidrome/releases/download/v${ND_VERSION}/navidrome_${ND_VERSION}_linux_amd64.tar.gz"
tar xzf nd.tgz navidrome

noise() { # seconds, output
  ffmpeg -loglevel error -y -filter_complex "anoisesrc=d=$1:c=white:r=44100:a=0.5[a];anoisesrc=d=$1:c=white:r=44100:a=0.5:seed=7[b];[a][b]amerge=inputs=2" \
    -sample_fmt s16 -metadata artist="Test Artist" -metadata album="$3" -metadata title="$4" "$2"
}
mkdir -p "music/Big Animated" "music/Small Animated" "music/Still Album"
noise 300 "music/Big Animated/01.flac" "Big Animated" "Long Song A"
noise 30 "music/Small Animated/01.flac" "Small Animated" "Short Song"
noise 300 "music/Still Album/01.flac" "Still Album" "Long Song B"
# A 1000×1000 animated cover like Apple Music motion art (about 13 MB as GIF), a small one, and a still one.
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=1000x1000:rate=24:duration=10" -vf "split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 "music/Big Animated/cover.gif"
ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=400x400:rate=10:duration=2" -vf "split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 "music/Small Animated/cover.gif"
ffmpeg -loglevel error -y -f lavfi -i "testsrc=size=800x800" -frames:v 1 "music/Still Album/cover.jpg"
ls -la music/*

ND_MUSICFOLDER="$DIR/music" ND_DATAFOLDER="$DIR/data" ND_PORT=4533 ND_ADDRESS=127.0.0.1 ND_SCANNER_SCHEDULE=0 \
  ND_ENABLEEXTERNALSERVICES=false ND_LOGLEVEL=info nohup ./navidrome > "$DIR/navidrome.log" 2>&1 &
for i in $(seq 1 30); do curl -s localhost:4533/ping >/dev/null && break; sleep 1; done
curl -s -X POST localhost:4533/auth/createAdmin -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' >/dev/null
A="u=admin&p=admin&v=1.16.1&c=ci&f=json"
curl -s "localhost:4533/rest/startScan?$A" >/dev/null
for i in $(seq 1 60); do
  sleep 2
  curl -s "localhost:4533/rest/getScanStatus?$A" | grep -q '"scanning":false' && break
done
curl -s "localhost:4533/rest/getAlbumList2?type=alphabeticalByName&$A"; echo

nohup python3 "$GITHUB_WORKSPACE/scripts/shape.py" 540000 5533 4533 > "$DIR/proxy.log" 2>&1 &
sleep 1
curl -s -o /dev/null -w "Test link check: %{http_code} in %{time_total}s\n" "localhost:5533/rest/ping?$A"
