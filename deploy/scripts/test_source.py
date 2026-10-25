import importlib.machinery, importlib.util, io, os, subprocess, sys, tarfile, tempfile, unittest

loader = importlib.machinery.SourceFileLoader("siansa_source", os.path.join(os.path.dirname(__file__), "siansa-source"))
spec = importlib.util.spec_from_loader("siansa_source", loader)
ss = importlib.util.module_from_spec(spec)
loader.exec_module(ss)


def archive(names):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for name in names:
            info = tarfile.TarInfo(name)
            info.size = 1
            tar.addfile(info, io.BytesIO(b"x"))
    return buf.getvalue()


class SourceTest(unittest.TestCase):
    def setUp(self):
        ss.FDROID_DIR = tempfile.mkdtemp()
        os.makedirs(ss.FDROID_DIR + "/repo")
        os.makedirs(ss.FDROID_DIR + "/tmp")

    def rejected(self, data):
        with self.assertRaises(SystemExit) as e:
            ss.check(data)
        return e.exception.code == 2

    def test_keeps_a_good_archive_under_fdroids_source_name(self):
        data = archive(["siansa-0.31.0beta/LICENSE", "siansa-0.31.0beta/app/build.gradle.kts"])
        ss.check(data)
        dest = ss.save("1234", data)
        self.assertEqual(os.path.basename(dest), "app.siansa_1234_src.tar.gz")
        self.assertEqual(open(dest, "rb").read(), data)
        self.assertEqual(os.listdir(ss.FDROID_DIR + "/tmp"), [])

    def test_a_kept_version_is_not_replaced(self):
        ss.save("7", b"first")
        ss.save("7", b"second")
        self.assertEqual(open(ss.FDROID_DIR + "/repo/app.siansa_7_src.tar.gz", "rb").read(), b"first")

    def test_rejects_keys_odd_paths_missing_license_and_other_folders(self):
        for names in [
            ["siansa-1/LICENSE", "siansa-1/app/siansa.keystore"],
            ["siansa-1/LICENSE", "siansa-1/../evil"],
            ["siansa-1/LICENSE", "/etc/passwd"],
            ["siansa-1/README.md"],
            ["siansa-1/LICENSE", "other/x"],
            ["notsiansa/LICENSE"],
        ]:
            self.assertTrue(self.rejected(archive(names)), names)
        self.assertTrue(self.rejected(b"not a tar"))

    def test_rejects_a_bad_version_code(self):
        for code in ["", "12a", "1;rm", "-1", "1234567890"]:
            with self.assertRaises(SystemExit):
                ss.main(["siansa-source", code])

    def test_a_real_git_archive_passes(self):
        root = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                              cwd=os.path.dirname(os.path.abspath(__file__)))
        if root.returncode != 0:
            self.skipTest("not in a git checkout")
        data = subprocess.run(["git", "archive", "--format=tar.gz", "--prefix=siansa-test/", "HEAD"],
                              capture_output=True, cwd=root.stdout.strip()).stdout
        names = tarfile.open(fileobj=io.BytesIO(data), mode="r:gz").getnames()
        if "siansa-test/LICENSE" not in names:
            self.skipTest("LICENSE not committed yet")
        ss.check(data)
        self.assertNotIn("siansa-test/app/siansa.keystore", names)


if __name__ == "__main__":
    unittest.main()
