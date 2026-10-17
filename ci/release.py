"""Plan version-bump releases and publish verified APKs without replacing published assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

VERSION = re.compile(r"\d+\.\d+\.\d+")
BUMP = re.compile(r"chore\(release\): bump version to (\d+\.\d+\.\d+)")


def command(*args, data=None):
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return result.stdout.decode().strip()


def git(*args):
    return command("git", *args)


def metadata(text):
    name = re.search(r'versionName\s*=\s*"(\d+\.\d+\.\d+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code:
        raise ValueError("Missing stable version name/code in app/build.gradle.kts")
    return name.group(1), int(code.group(1))


def resolve(ref):
    sha = git("rev-parse", "--verify", "--end-of-options", ref + "^{commit}")
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Invalid source commit")
    command("git", "merge-base", "--is-ancestor", sha, "origin/main")
    return sha


def source_version(sha):
    return metadata(git("show", sha + ":app/build.gradle.kts"))


def release_record(sha, require_bump):
    version, code = source_version(sha)
    if require_bump:
        match = BUMP.fullmatch(git("show", "-s", "--format=%s", sha))
        if not match:
            return None
        prior_version, prior_code = source_version(sha + "^")
        if match.group(1) != version or code <= prior_code or version_key(version) <= version_key(prior_version):
            raise ValueError("Version-bump subject, version name and increasing version code must agree")
    return {"sha": sha, "version": version, "code": code, "time": git("show", "-s", "--format=%cI", sha)}


def plan(event_name, event):
    if event_name == "workflow_dispatch":
        return [release_record(resolve(event["inputs"]["ref"]), False)]
    before, after = event["before"], resolve(event["after"])
    scope = after if before == "0" * 40 else before + ".." + after
    commits = git("rev-list", "--reverse", "--topo-order", scope).splitlines()
    records = [release_record(sha, True) for sha in commits]
    return [record for record in records if record is not None]


def version_key(version):
    if not VERSION.fullmatch(version):
        raise ValueError("Expected a stable major.minor.patch version")
    return tuple(int(part) for part in version.split("."))


def previous_tag(version):
    tags = [tag for tag in git("tag", "--list", "v*").splitlines() if VERSION.fullmatch(tag[1:])]
    older = [tag for tag in tags if version_key(tag[1:]) < version_key(version)]
    return max(older, key=lambda tag: version_key(tag[1:]), default=None)


def change_lines(sha, previous):
    scope = previous + ".." + sha if previous else sha
    lines = git("log", "--reverse", "--no-merges", "--format=%s", scope).splitlines()
    changes = [line for line in lines if not BUMP.fullmatch(line)]
    # Commit subjects are release-note data, never executable input or account mentions.
    safe = [line.replace("@", "&#64;").replace("<", "&lt;").replace(">", "&gt;") for line in changes]
    return "\n".join("- " + line for line in safe) or "- Maintenance version and build metadata update."


def notes(record, digest, repository):
    try:
        body = git("show", f"{record['sha']}:releases/{record['version']}.md")
    except subprocess.CalledProcessError:
        body = "## Changes\n\n- Maintenance version and build metadata update."
    return (body + "\n\n## APK\n\n"
            f"- Version: **{record['version']} / code {record['code']}**.\n"
            "- Signed release APK; non-debuggable. Release unit tests and lint passed.\n"
            f"- SHA-256: `{digest}`.\n")


def verify_apk(apk, record, tools, certificate):
    signature = command(str(tools / "apksigner"), "verify", "--print-certs", str(apk))
    if f"certificate SHA-256 digest: {certificate}" not in signature:
        raise ValueError("Unexpected APK signing certificate")
    badging = command(str(tools / "aapt2"), "dump", "badging", str(apk))
    expected = ["name='ymg.pwcca.privacydisplayplus'", f"versionCode='{record['code']}'", f"versionName='{record['version']}'"]
    if not all(value in badging for value in expected) or "application-debuggable" in badging:
        raise ValueError("APK package/version/build type mismatch")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        entry = archive.read("assets/xposed_init").decode().strip()
        if entry != "ymg.pwcca.privacydisplayplus.PrivacyDisplayModule":
            raise ValueError("Unexpected Xposed entry point")
        if "assets/xposed_init" not in names or any(name.endswith((".mp4", ".m4a", ".jks", ".keystore")) for name in names):
            raise ValueError("Unexpected release APK contents")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    Path(str(apk) + ".sha256").write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
    return digest


def api(repository, endpoint, payload=None):
    args = ["gh", "api", f"repos/{repository}/{endpoint}"]
    if payload is not None:
        args += ["--method", "POST", "--input", "-"]
    data = None if payload is None else json.dumps(payload).encode()
    return json.loads(command(*args, data=data))


def ensure_tag(repository, record):
    tag = "v" + record["version"]
    existing = git("tag", "--list", tag)
    if existing:
        if git("rev-parse", tag + "^{commit}") != record["sha"]:
            raise ValueError("Existing version tag points to a different commit")
        return tag
    obj = api(repository, "git/tags", {"tag": tag, "message": "PrivacyDisplayPlus " + record["version"],
        "object": record["sha"], "type": "commit", "tagger": {"name": "github-actions[bot]",
        "email": "41898282+github-actions[bot]@users.noreply.github.com", "date": record["time"]}})
    api(repository, "git/refs", {"ref": "refs/tags/" + tag, "sha": obj["sha"]})
    return tag


def publish(record, directory, repository):
    tag = ensure_tag(repository, record)
    releases = api(repository, "releases?per_page=100")
    existing = next((release for release in releases if release["tag_name"] == tag), None)
    if existing and not existing["draft"]:
        print("Already published; existing release and assets preserved: " + existing["html_url"])
        return
    apk = directory / f"PrivacyDisplayPlus-{record['version']}.apk"
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    body = directory / "notes.md"
    body.write_text(notes(record, digest, repository), encoding="utf-8")
    if existing is None:
        command("gh", "release", "create", tag, "--repo", repository, "--target", record["sha"],
                "--verify-tag", "--title", "Privacy Display+ " + record["version"], "--notes-file", str(body), "--draft")
    command("gh", "release", "upload", tag, str(apk), str(apk) + ".sha256", "--repo", repository, "--clobber")
    draft = next(release for release in api(repository, "releases?per_page=100") if release["tag_name"] == tag)
    assets = {asset["name"]: asset for asset in draft["assets"]}
    if assets[apk.name]["digest"] != "sha256:" + digest:
        raise ValueError("Uploaded APK digest mismatch")
    command("gh", "api", f"repos/{repository}/releases/{draft['id']}", "--method", "PATCH", "--input", "-",
            data=json.dumps({"draft": False, "make_latest": "legacy", "body": body.read_text(encoding="utf-8")}).encode())
    print("Published " + draft["html_url"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["plan", "verify", "publish"])
    parser.add_argument("--sha")
    parser.add_argument("--directory", type=Path, default=Path("dist"))
    args = parser.parse_args()
    if args.mode == "plan":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        records = plan(os.environ["GITHUB_EVENT_NAME"], event)
        publish_requested = os.environ["GITHUB_EVENT_NAME"] == "push" or str(event.get("inputs", {}).get("publish", "false")).lower() == "true"
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write("matrix=" + json.dumps({"include": records}) + "\n")
            output.write("has_releases=" + str(bool(records)).lower() + "\n")
            output.write("publish=" + str(publish_requested).lower() + "\n")
        print(json.dumps(records, indent=2))
        return
    record = release_record(resolve(args.sha), False)
    if args.mode == "verify":
        tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
        apk = args.directory / f"PrivacyDisplayPlus-{record['version']}.apk"
        digest = verify_apk(apk, record, tools, os.environ["SIGNING_CERT_SHA256"])
        print("Verified release APK: " + digest)
        return
    publish(record, args.directory, os.environ["GITHUB_REPOSITORY"])


if __name__ == "__main__":
    main()
