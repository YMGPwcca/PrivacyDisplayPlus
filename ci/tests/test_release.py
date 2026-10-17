import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class VersionTests(unittest.TestCase):
    def test_version_name_and_code(self):
        self.assertEqual(("1.3.2", 18), release.metadata('versionCode = 18\nversionName = "1.3.2"'))
        with self.assertRaises(ValueError):
            release.metadata("versionName '1.3.2'")

    def test_numeric_version_order(self):
        self.assertGreater(release.version_key("1.10.0"), release.version_key("1.9.9"))
        with self.assertRaises(ValueError):
            release.version_key("1.3.2; command")

    def test_published_release_is_never_overwritten(self):
        with patch.object(release, "ensure_tag", return_value="v1.3.1"), patch.object(release, "api", return_value=[
                {"tag_name": "v1.3.1", "draft": False, "html_url": "https://example.test/release"}]), patch.object(release, "command") as command:
            release.publish({"version": "1.3.1"}, Path("missing-artifacts"), "owner/repo")
            command.assert_not_called()

    def test_conflicting_tag_is_rejected(self):
        with patch.object(release, "git", side_effect=["v1.3.1", "b" * 40]), patch.object(release, "api") as api:
            with self.assertRaises(ValueError):
                release.ensure_tag("owner/repo", {"version": "1.3.1", "sha": "a" * 40})
            api.assert_not_called()

    def test_uploaded_digest_mismatch_prevents_publication(self):
        self.check_draft_publication("sha256:wrong", fails=True)

    def test_verified_draft_publishes_with_semantic_latest_policy(self):
        import hashlib
        self.check_draft_publication("sha256:" + hashlib.sha256(b"apk").hexdigest(), fails=False)

    def check_draft_publication(self, digest, fails):
        with tempfile.TemporaryDirectory(prefix="pdplus-release-tests-") as name:
            directory = Path(name).resolve()
            self.assertEqual(directory.parent, Path(tempfile.gettempdir()).resolve())
            self.assertTrue(directory.name.startswith("pdplus-release-tests-"))
            (directory / "PrivacyDisplayPlus-1.3.2.apk").write_bytes(b"apk")
            draft = {"id": 10, "tag_name": "v1.3.2", "draft": True, "html_url": "https://example.test/release", "assets": [
                {"name": "PrivacyDisplayPlus-1.3.2.apk", "digest": digest}]}
            with patch.object(release, "ensure_tag", return_value="v1.3.2"), patch.object(release, "api", side_effect=[[], [draft]]), \
                    patch.object(release, "notes", return_value="notes"), patch.object(release, "command") as command:
                if fails:
                    with self.assertRaises(ValueError):
                        release.publish({"version": "1.3.2", "sha": "a" * 40}, directory, "owner/repo")
                    self.assertFalse(any("PATCH" in call.args for call in command.call_args_list))
                else:
                    release.publish({"version": "1.3.2", "sha": "a" * 40}, directory, "owner/repo")
                    call = command.call_args_list[-1]
                    self.assertIn("PATCH", call.args)
                    self.assertEqual({"draft": False, "make_latest": "legacy", "body": "notes"}, json.loads(call.kwargs["data"]))


class PlanningTests(unittest.TestCase):
    def setUp(self):
        self.old_directory = Path.cwd()
        self.directory = tempfile.TemporaryDirectory(prefix="pdplus-release-tests-")
        path = Path(self.directory.name).resolve()
        self.assertEqual(path.parent, Path(tempfile.gettempdir()).resolve())
        self.assertTrue(path.name.startswith("pdplus-release-tests-"))
        os.chdir(path)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Release Tests")
        self.git("config", "user.email", "tests@example.invalid")
        Path("app").mkdir()
        self.initial = self.commit("1.0.0", 1, "feat: initial")

    def tearDown(self):
        os.chdir(self.old_directory)
        # Only the verified, newly created test directory is removed.
        self.directory.cleanup()

    def git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.DEVNULL).decode().strip()

    def commit(self, version, code, subject):
        Path("app/build.gradle.kts").write_text(f'versionCode = {code}\nversionName = "{version}"\n')
        self.git("add", "app/build.gradle.kts")
        self.git("commit", "--allow-empty", "-m", subject)
        sha = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/main", sha)
        return sha

    def test_batch_push_finds_all_bumps_and_ignores_other_commits(self):
        first = self.commit("1.0.1", 2, "chore(release): bump version to 1.0.1")
        self.commit("1.0.1", 2, "docs: improve instructions")
        second = self.commit("1.0.2", 3, "chore(release): bump version to 1.0.2")
        rows = release.plan("push", {"before": self.initial, "after": second})
        self.assertEqual([first, second], [row["sha"] for row in rows])

    def test_non_release_push_has_no_builds(self):
        sha = self.commit("1.0.0", 1, "ci: add release automation")
        self.assertEqual([], release.plan("push", {"before": self.initial, "after": sha}))

    def test_version_subject_mismatch_fails(self):
        sha = self.commit("1.0.1", 2, "chore(release): bump version to 1.0.2")
        with self.assertRaises(ValueError):
            release.plan("push", {"before": self.initial, "after": sha})

    def test_non_increasing_version_code_fails(self):
        sha = self.commit("1.0.1", 1, "chore(release): bump version to 1.0.1")
        with self.assertRaises(ValueError):
            release.plan("push", {"before": self.initial, "after": sha})

    def test_manual_build_requires_source_on_main(self):
        rows = release.plan("workflow_dispatch", {"inputs": {"ref": self.initial}})
        self.assertEqual(self.initial, rows[0]["sha"])
        self.git("checkout", "-b", "unmerged")
        Path("other").write_text("not on main")
        self.git("add", "other")
        self.git("commit", "-m", "feat: unmerged")
        with self.assertRaises(subprocess.CalledProcessError):
            release.plan("workflow_dispatch", {"inputs": {"ref": self.git("rev-parse", "HEAD")}})

    def test_maintenance_notes_omit_commit_details(self):
        self.git("tag", "v1.0.0", self.initial)
        sha = self.commit("1.0.1", 2, "chore(release): bump version to 1.0.1")
        body = release.notes(release.release_record(sha, True), "0" * 64, "owner/repo")
        self.assertIn("Maintenance version and build metadata update", body)
        self.assertNotIn("commit time", body)
        self.assertNotIn("Asia/Saigon", body)
        self.assertNotIn(sha, body)
        self.assertNotIn("/commit/", body)


if __name__ == "__main__":
    unittest.main()
