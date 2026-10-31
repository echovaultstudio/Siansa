"""The made-up library the showcase screenshots are taken with (issue #741): artists, albums, songs and lyrics.

Every name here is invented for Siansa's screenshots. Albums name the look of their generated cover (covers.py).
"""

# (artist, genre, label, [(album, year, cover style, palette, [songs])])
ARTISTS = [
    ("Glass Harbour", "Dream Pop", "Tidewater Records", [
        ("Low Tide", 2024, "waves", "harbour", [
            "Low Tide", "Saltwater Letters", "Paper Boats", "Lighthouse Static", "Undertow", "Harbour Lights",
            "Slow Return"]),
        ("Paper Lanterns", 2026, "lanterns", "dusk", [
            "Paper Lanterns", "Kindling", "The Long Way Round", "Ferris Wheel at Noon", "Glow Worms", "Ember Song"]),
    ]),
    ("Neon Parish", "Synthwave", "Night Shift Audio", [
        ("Night Sessions", 2025, "sunset", "neon", [
            "Midnight Drive", "Arcade Hearts", "Velocity", "Streetlamp Choir", "Rearview", "Magenta Skyline",
            "Last Exit", "Dawn Patrol"]),
        ("Chrome Psalms", 2023, "chrome", "chrome", [
            "Chrome Psalm", "Static Saints", "Pulse Engine", "Mirror Hall", "Afterhours"]),
    ]),
    ("Hollow Pines", "Indie Folk", "Cedar & Stone", [
        ("Cedar Light", 2022, "hills", "forest", [
            "Cedar Light", "Woodsmoke", "Northern Gate", "Kettle On", "Moss and Iron", "River Road", "Lantern Hill"]),
    ]),
    ("Velvet Coast", "Soul", "Golden Thread", [
        ("Slow Bloom", 2026, "blobs", "peach", [
            "Slow Bloom", "Honeyed", "Sunday Kitchen", "Hold the Phone", "Velvet Hour", "Easy Does It"]),
    ]),
    ("Mara Lux", "House", "Lumen Works", [
        ("Afterglow", 2024, "bauhaus", "tropic", [
            "Afterglow", "Bodies in Motion", "Neon Fern", "Tidal", "Warehouse Sunrise", "Closer Now", "Prism"]),
        ("Live at Harbour Hall", 2025, "halftone", "magenta", [
            "Intro (Live)", "Bodies in Motion (Live)", "Tidal (Live)", "Prism (Live)", "Afterglow (Live)"]),
    ]),
    ("Static Avenue", "Indie Rock", "Loud Weather", [
        ("Signal Fire", 2023, "type-stripes", "signal", [
            "Signal Fire", "Overpass", "Run the Lights", "Feedback Loop", "Cold Coffee", "Satellite Town"]),
    ]),
    ("Kel Arden", "Lo-Fi", "Paper Moon Music", [
        ("Paper Sun", 2026, "sun", "paper", [
            "Paper Sun", "Window Seat", "Corner Shop", "Rain Check", "Late Tram", "Blue Hour Study", "Pages"]),
        ("Night Bus Beats", 2025, "halftone", "night", [
            "Night Bus", "Sodium Lights", "Last Stop", "Fogged Glass", "Home by Two"]),
    ]),
    ("Pale Signal", "Ambient", "Quiet Field", [
        ("Quiet Weather", 2025, "fog", "fog", [
            "Quiet Weather", "Low Cloud", "Drizzle Choir", "Barometer", "Slow Front"]),
    ]),
    ("Ewan Hale", "Singer-Songwriter", "Small Room Records", [
        ("Small Hours", 2024, "moon", "midnight", [
            "Small Hours", "Kitchen Light", "Letter I Never Sent", "Streetlight Waltz", "Morning Will Come",
            "Still Here"]),
    ]),
    ("Sunborn", "Downtempo", "Lumen Works", [
        ("Golden Hour Tapes", 2025, "retro", "golden", [
            "Golden Hour", "Cassette Summer", "Long Shadows", "Heat Haze", "Sunset Boulevard Tape", "Fade Out"]),
    ]),
    ("The Copper Lanes", "Jazz", "Blue Corner", [
        ("Blue Corners", 2021, "type-rings", "copper", [
            "Blue Corners", "Copper Lane", "Smoke Rings", "Late Set", "Brushes", "Ballad for June", "Encore"]),
    ]),
    ("Iris Okafor", "Neo-Soul", "Golden Thread", [
        ("Honey & Thunder", 2026, "blobs", "honey", [
            "Honey & Thunder", "Gravity", "Slow Burn", "Sundress", "Tell Me Twice", "Home Again"]),
    ]),
    ("Midnight Atlas", "Techno", "Night Shift Audio", [
        ("Coordinates", 2024, "grid", "atlas", [
            "Coordinates", "North by Night", "Grid Lock", "Meridian", "Lumen", "Terminus"]),
    ]),
    ("Rosa Venn", "Modern Classical", "Quiet Field", [
        ("Letters to Morning", 2023, "line", "paperlight", [
            "Letters to Morning", "First Light", "Linen", "Etude for Rain", "The Open Window"]),
    ]),
    ("Loma Drift", "Chillwave", "Tidewater Records", [
        ("Sea Glass", 2025, "waves", "seaglass", [
            "Sea Glass", "Pastel Shore", "Driftwood", "Summer Static", "Tide Pool", "Pier at Dusk"]),
    ]),
    ("Arlo Grey", "Indie Pop", "Loud Weather", [
        ("Polaroid Summer", 2026, "bauhaus", "polaroid", [
            "Polaroid Summer", "Rooftop", "Lemonade Stand", "Bike Bells", "Sunburn", "Back to School"]),
    ]),
    ("Brass Meridian", "Afrobeat", "Sundial Sounds", [
        ("Sundial", 2024, "rings", "sundial", [
            "Sundial", "Market Day", "Brass Parade", "Heatwave", "Long Walk Home", "Dance Floor Sermon"]),
    ]),
    ("Tove Marin", "Electropop", "Lumen Works", [
        ("Ultraviolet Hearts", 2025, "stripes", "ultraviolet", [
            "Ultraviolet", "Glitter Rain", "Heartbeat Machine", "Night Swim", "Electric Blue", "Goodnight Neon"]),
    ]),
    ("Juno Fields", "Indie Pop", "Paper Moon Music", [
        ("Kite Season", 2023, "hills", "kite", [
            "Kite Season", "Meadow Run", "Picnic Weather", "Dandelion", "Long Grass"]),
    ]),
    ("Northbound Hymns", "Post-Rock", "Cedar & Stone", [
        ("Stillwater", 2022, "mountains", "glacier", [
            "Stillwater", "Glacier Bell", "The Long Thaw", "Northbound", "Aurora"]),
    ]),
]

# Synced lyrics (written for the screenshots) for a few songs: (artist, title) -> [(seconds, line)].
LYRICS = {
    ("Glass Harbour", "Harbour Lights"): [
        (12, "The boats come in when the light goes low"),
        (18, "Salt on the window, a radio glow"),
        (25, "I count the harbour lights one by one"),
        (31, "Till every last one says the day is done"),
        (38, "Hold on, hold on, the tide will turn"),
        (44, "Hold on, the lights will learn"),
        (51, "To find their way back home to you"),
        (58, "Like I always do"),
    ],
    ("Velvet Coast", "Slow Bloom"): [
        (10, "Morning comes in slow like honey"),
        (16, "Sun across the kitchen floor"),
        (22, "Nothing here is in a hurry"),
        (28, "We could stay a little more"),
        (35, "Slow bloom, slow bloom"),
        (41, "Opening up in this little room"),
    ],
    ("Neon Parish", "Midnight Drive"): [
        (14, "City lights in the rear-view mirror"),
        (20, "Every mile feels a little clearer"),
        (27, "Radio playing our favourite song"),
        (33, "Midnight drive, we can't go wrong"),
    ],
}
