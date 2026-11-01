### Fixed
- **Beats clashed in some DJ blends.** A song queued while another played (a radio just started, a reshaped mix)
  had no measured start yet when its blend was planned, so it got a long fixed blend that ignored its beat; its
  measurements are now fetched as soon as it's queued. A song that ends on a steady beat no longer blends at length
  into a song that hasn't been measured: the blend is short. A beat-matched blend into a short song is laid over
  fewer bars instead of being blended out of step.
