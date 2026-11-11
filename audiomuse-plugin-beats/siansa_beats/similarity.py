"""Similar albums and artists from AudioMuse-AI's sound analysis.

Every analysed song has a MusiCNN embedding (a vector describing how it sounds). An album's sound is the
average direction of its songs' vectors, and so is an artist's; two albums (or artists) are similar when
those directions are close (cosine similarity).

The lists are kept varied: an album list holds at most :data:`PER_ARTIST` albums by one artist, and other
editions of the same album (deluxe, remaster, …) are left out. Only plain numpy is used, in blocks, so a
library of tens of thousands of albums fits in little memory.
"""

import re
from dataclasses import dataclass, field

import numpy as np

ALGORITHM_VERSION = 1
LIMIT = 20
PER_ARTIST = 2
MIN_SONGS = 1
BLOCK = 512

_EDITION = re.compile(
    r"\s*[\(\[][^\)\]]*(deluxe|remaster|edition|expanded|anniversary|bonus|version|mono|stereo|live)"
    r"[^\)\]]*[\)\]]\s*$",
    re.IGNORECASE,
)


def norm(text):
    """Lower case, single spaces: how names are compared."""
    return " ".join(str(text or "").casefold().split())


def base_title(album):
    """An album title without edition notes at the end, so editions of one album match."""
    title = str(album or "")
    while True:
        stripped = _EDITION.sub("", title)
        if stripped == title:
            return norm(title)
        title = stripped


@dataclass
class Group:
    """An album or artist, with the songs it was built from."""

    key: str
    name: str
    artist: str
    track_id: str  # one of its songs, so the app can find the album or artist on its own server
    vectors: list = field(default_factory=list)


def artist_of(album_artist, author):
    return (album_artist or author or "").strip()


def album_key(artist, album):
    return f"{norm(artist)}\x1f{norm(album)}"


def group_tracks(tracks):
    """``tracks``: ``(item_id, album, album_artist, author, vector)``. Returns ``(albums, artists)`` dicts."""
    albums, artists = {}, {}
    for item_id, album, album_artist, author, vector in tracks:
        if vector is None:
            continue
        artist = artist_of(album_artist, author)
        if not artist:
            continue
        if album:
            key = album_key(artist, album)
            group = albums.get(key) or albums.setdefault(key, Group(key, album, artist, item_id))
            group.vectors.append(vector)
        # Songs count for the artist who performs them, which on a compilation is not the album artist.
        performer = (author or album_artist or "").strip()
        akey = norm(performer)
        group = artists.get(akey) or artists.setdefault(akey, Group(akey, performer, performer, item_id))
        group.vectors.append(vector)
    return albums, artists


def centroids(groups):
    """Unit-length mean vector of each group with enough songs, as ``(keys, matrix)``."""
    keys, rows = [], []
    for key, group in groups.items():
        if len(group.vectors) < MIN_SONGS:
            continue
        mean = np.mean(np.asarray(group.vectors, dtype=np.float32), axis=0)
        length = float(np.linalg.norm(mean))
        if length == 0 or not np.isfinite(length):
            continue
        keys.append(key)
        rows.append(mean / length)
    if not rows:
        return [], np.zeros((0, 0), dtype=np.float32)
    return keys, np.vstack(rows).astype(np.float32)


def _neighbours(matrix, wanted, progress=None):
    """For every row, the indexes of the ``wanted`` most similar other rows, best first, with their scores."""
    count = matrix.shape[0]
    wanted = min(wanted, max(count - 1, 0))
    out = []
    for start in range(0, count, BLOCK):
        scores = matrix[start:start + BLOCK] @ matrix.T
        for row in range(scores.shape[0]):
            scores[row, start + row] = -np.inf  # never itself
        if wanted == 0:
            out.extend(([], []) for _ in range(scores.shape[0]))
            continue
        top = np.argpartition(-scores, wanted - 1, axis=1)[:, :wanted]
        for row in range(scores.shape[0]):
            order = top[row][np.argsort(-scores[row, top[row]], kind="stable")]
            out.append((order.tolist(), scores[row, order].tolist()))
        if progress:
            progress(min(start + BLOCK, count), count)
    return out


def rank_albums(albums, limit=LIMIT, per_artist=PER_ARTIST, progress=None):
    """``{album_key: [candidate, …]}``; each candidate has album, artist, track_id and score (0..1)."""
    keys, matrix = centroids(albums)
    # Look further than the limit, so there are enough left after the per-artist cap and the editions.
    near = _neighbours(matrix, limit * 4 + 8, progress)
    result = {}
    for index, key in enumerate(keys):
        source = albums[key]
        same = (norm(source.artist), base_title(source.name))
        picked, per = [], {}
        for other, score in zip(*near[index]):
            candidate = albums[keys[other]]
            artist = norm(candidate.artist)
            if (artist, base_title(candidate.name)) == same:
                continue
            if per.get(artist, 0) >= per_artist:
                continue
            per[artist] = per.get(artist, 0) + 1
            picked.append({
                "album": candidate.name, "artist": candidate.artist, "track_id": candidate.track_id,
                "score": round(max(0.0, float(score)), 4),
            })
            if len(picked) >= limit:
                break
        result[key] = picked
    return result


def rank_artists(artists, limit=LIMIT, progress=None):
    """``{artist_key: [candidate, …]}``; each candidate has artist, track_id and score (0..1)."""
    keys, matrix = centroids(artists)
    near = _neighbours(matrix, limit, progress)
    return {
        key: [
            {"artist": artists[keys[other]].name, "track_id": artists[keys[other]].track_id,
             "score": round(max(0.0, float(score)), 4)}
            for other, score in zip(*near[index])
        ]
        for index, key in enumerate(keys)
    }
