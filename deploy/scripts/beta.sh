# For bash (the usual login shell on Ubuntu).
# Short commands for the Siansa beta on this server. Load them once per login, or add to ~/.bashrc:
#   echo '. ~/siansa-deploy/scripts/beta.sh' >> ~/.bashrc && . ~/.bashrc
# Then type beta-help.
SIANSA_KIT="${SIANSA_KIT:-$HOME/siansa-deploy}"

_beta_server() { (cd "$SIANSA_KIT" && sudo docker compose exec -T signup python -I /app/server.py "$@"); }
_beta_need() { [ -n "$1" ] || { echo "Give the tester's email, e.g. $2 name@example.com" >&2; return 1; }; }

beta-help() {
	cat <<'HELP'
Siansa beta
  beta                  everyone who signed up, with their status
  beta-count            places taken, waiting, undecided
  beta-today            today's summary (sign-ups, F-Droid downloads and devices, reports)
  beta-mail-summary     email today's summary to you now

  beta-accept EMAIL     approve someone and send their welcome email (like the Approve button)
  beta-deny EMAIL       turn someone down with a short, polite email (like the Deny button)
  beta-remove EMAIL     remove someone completely (tests, or anyone who asks); no email; the next one waiting moves up
  beta-revoke EMAIL     take away a tester's place but keep their sign-up; the next one waiting moves up
  beta-link EMAIL       show the private F-Droid address for someone already approved
  beta-resend           send the Approve/Deny emails for undecided sign-ups again

  beta-play             approved Google Play emails, comma separated, for Play Console
  beta-fdroid           approved F-Droid emails
  beta-csv              everything as CSV

  beta-repo-link        show the private F-Droid address
  beta-new-link         make a new private address; the old one stops at once and testers are emailed it
  beta-reports          the latest problem reports from the app
  beta-logs             the sign-up service's log (Ctrl+C to stop)
  beta-restart          restart the sign-up service (after changing .env)
HELP
}

beta() { sudo sh "$SIANSA_KIT/scripts/signups"; }
beta-count() { curl -s https://siansa.app/signup/count; echo; }
beta-today() { _beta_server stats; }
beta-mail-summary() { _beta_server digest; }
beta-accept() { _beta_need "$1" beta-accept && _beta_server accept "$1"; }
beta-deny() { _beta_need "$1" beta-deny && _beta_server deny "$1"; }
beta-remove() { _beta_need "$1" beta-remove && _beta_server forget "$1"; }
beta-revoke() { _beta_need "$1" beta-revoke && sudo sh "$SIANSA_KIT/scripts/signups" revoke "$1"; }
beta-link() { _beta_need "$1" beta-link && sudo sh "$SIANSA_KIT/scripts/signups" link "$1"; }
beta-resend() { _beta_server resend; }
beta-play() { sudo sh "$SIANSA_KIT/scripts/signups" play; }
beta-fdroid() { sudo sh "$SIANSA_KIT/scripts/signups" fdroid; }
beta-csv() { sudo sh "$SIANSA_KIT/scripts/signups" csv; }
beta-repo-link() { (cd "$SIANSA_KIT" && sudo sh scripts/repo-link); }
beta-new-link() {
	printf 'Make a new private F-Droid address? The old one stops working at once. [y/N] '
	read -r answer
	[ "$answer" = y ] || [ "$answer" = Y ] || { echo "Nothing changed."; return 0; }
	(cd "$SIANSA_KIT" && sudo sh scripts/repo-link rotate)
}
beta-reports() { sudo ls -lt "${SIANSA_SIGNUPS:-/srv/siansa-signups}/reports" 2>/dev/null | head -n 15; }
beta-logs() { (cd "$SIANSA_KIT" && sudo docker compose logs -f --tail 50 signup); }
beta-restart() { (cd "$SIANSA_KIT" && sudo docker compose up -d --force-recreate signup); }
