import importlib.machinery, importlib.util, io, os, sys, tarfile, tempfile, unittest

loader = importlib.machinery.SourceFileLoader("siansa_site_update", os.path.join(os.path.dirname(__file__), "siansa-site-update"))
spec = importlib.util.spec_from_loader("siansa_site_update", loader)
su = importlib.util.module_from_spec(spec)
loader.exec_module(su)


def archive(files, extra=None):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for name, content in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(content)
            tar.addfile(info, io.BytesIO(content))
        for info in extra or []:
            tar.addfile(info)
    return buf.getvalue()


class SiteUpdateTest(unittest.TestCase):
    def setUp(self):
        d = tempfile.mkdtemp()
        su.SITE_DIR, su.EMAILS_DIR, su.DOMAIN_FILE = d + "/site", d + "/emails", d + "/domain"
        open(su.DOMAIN_FILE, "w").write("siansa.app\n")
        self.exit = None

    def collect(self, data):
        try:
            return su.collect(data)
        except SystemExit as e:
            self.exit = e.code

    def test_installs_pages_and_emails_and_fills_in_the_domain(self):
        files = su.collect(archive({"site/index.html": b"<a>{{DOMAIN}}</a>", "site/style.css": b"x", "emails/deny.txt": b"Subject: S\n\nB"}))
        su.install(su.SITE_DIR, files["site"], "siansa.app")
        su.install(su.EMAILS_DIR, files["emails"])
        self.assertEqual(open(su.SITE_DIR + "/index.html").read(), "<a>siansa.app</a>")
        self.assertTrue(os.path.exists(su.EMAILS_DIR + "/deny.txt"))

    def test_files_not_in_the_archive_are_removed(self):
        os.makedirs(su.SITE_DIR)
        open(su.SITE_DIR + "/old.html", "w").write("x")
        su.install(su.SITE_DIR, {"index.html": b"i"}, "d")
        self.assertEqual(os.listdir(su.SITE_DIR), ["index.html"])

    def test_rejects_odd_names_types_links_and_missing_index(self):
        bad = [
            {"site/index.html": b"x", "site/../evil.html": b"x"},
            {"site/index.html": b"x", "/etc/passwd": b"x"},
            {"site/index.html": b"x", "site/run.sh": b"x"},
            {"site/index.html": b"x", "site/sub/a.html": b"x"},
            {"site/index.html": b"x", "other/a.txt": b"x"},
            {"site/style.css": b"x"},
        ]
        for files in bad:
            self.exit = None
            self.collect(archive(files))
            self.assertEqual(self.exit, 2, list(files))
        link = tarfile.TarInfo("site/link.html")
        link.type = tarfile.SYMTYPE
        link.linkname = "/etc/passwd"
        self.exit = None
        self.collect(archive({"site/index.html": b"x"}, [link]))
        self.assertEqual(self.exit, 2)

    def test_not_a_tar_is_rejected(self):
        self.collect(b"hello")
        self.assertEqual(self.exit, 2)


if __name__ == "__main__":
    unittest.main()
