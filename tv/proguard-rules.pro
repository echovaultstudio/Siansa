# The TV app (issue #860) uses the phone app's rules (app/proguard-rules.pro) as well as these.

# The playback service opens the TV screen by name (AppEntry.TV_ACTIVITY).
-keep class app.siansa.tvapp.TvActivity { <init>(); }
