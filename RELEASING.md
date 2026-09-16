# Making a release

A release is a version of the client that other people can download. It appears on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases), and anyone can download it
without a GitHub account.

Releasing is one button. You type the new version number, and a workflow does everything else:
it updates `pom.xml`, waits for the checks to pass, tags the commit, and builds the installers.

## Before you start

Make sure your changes are already merged into `main` and the checks passed. Look for the green
tick next to the newest commit on the
[main branch](https://github.com/PennStateCS/AFCT-Client/commits/main).

## Step 1: choose the new version number

Look at the current version on the [Releases page](https://github.com/PennStateCS/AFCT-Client/releases)
or in `pom.xml` near the top. The version has three numbers. Pick the new one like this:

- Fixed a bug, nothing else changed: increase the **last** number. 1.6.8 becomes 1.6.9.
- Added something new: increase the **middle** number and set the last to zero. 1.6.8 becomes 1.7.0.
- Changed something big, so old habits no longer work: increase the **first** number. 1.6.8 becomes 2.0.0.

## Step 2: press the button

1. Open the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions).
2. Click **Cut a release** in the list on the left.
3. Click the **Run workflow** button on the right.
4. Type the new version number, plain digits only: `1.6.9`. Leave the suffix box empty.
5. Click the green **Run workflow**.

That is the whole release. The workflow now:

1. updates `<version>` in `pom.xml` and commits that straight to `main` (no pull request needed;
   the commit changes nothing but the number),
2. runs the checks on that commit and waits for them to pass,
3. creates the tag (`v1.6.9`) and pushes it,
4. starts the **Release** build.

If anything goes wrong before the tag is made, nothing is tagged and nothing is published.

## Step 3: watch it build

Stay on the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions). First **Cut a
release** runs (a few minutes, most of it waiting for the checks), then a run called **Release**
starts. That one takes roughly ten minutes, because the installers are built on three different
machines at once, one for each thing people download:

| File | For |
| --- | --- |
| `AFCT-Client-1.6.9-windows.exe` | Windows |
| `AFCT-Client-1.6.9-macos-apple-silicon.dmg` | Macs made from late 2020 onwards |
| `AFCT-Client-1.6.9-macos-intel.dmg` | Older Intel Macs |
| `afct-client-v1.6.9.jar` | Anyone who would rather run it with their own Java |

When it finishes, your new release is on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases) with all four attached and a
list of what changed since last time.

If one platform fails, nothing is published at all. That is on purpose: a release with the
Windows installer missing is worse than no release, because people download the part that is
there and assume it is everything.

## A warning students will see

The installers are not signed yet, so both systems will say they do not recognise the program:

- **Windows** shows a blue "Windows protected your PC" box. The student clicks **More info**, then
  **Run anyway**.
- **macOS** refuses to open it. The student opens **System Settings**, goes to **Privacy and
  Security**, scrolls down, and clicks **Open Anyway** next to the message about AFCT Client.

This is worth fixing and is the next piece of work. It needs a code signing certificate for
Windows and an Apple Developer account for macOS, so it is a purchasing question before it is a
programming one. Until then, tell students to expect the warning, because a student who is not
expecting it will reasonably assume the download is broken.

## If it fails

Open the failed run in the Actions tab and read the step marked in red. The likely ones:

**"CI did not pass for ..."** — the checks failed on `main`, so nothing was tagged and nothing
was published. The version bump in `pom.xml` is already on `main` and is harmless. Fix whatever
broke the checks, then run **Cut a release** again with the **same** version number; it will see
the pom is already right and carry on from there.

**"v1.6.9 already exists."** — this version was already released, or someone started releasing
it. Check the [Releases page](https://github.com/PennStateCS/AFCT-Client/releases). If the old
attempt is broken and never got downloaded, remove the tag (see below) and run the workflow
again; otherwise pick the next number.

**The Release run failed after the tag was made.** — fix the cause, remove the tag (see below),
and press the button again with the same number.

### Removing a tag

```bash
git fetch --tags
git tag -d v1.6.9
git push origin --delete v1.6.9
```

If a release was already published, delete it on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases) as well, or run:

```bash
gh release delete v1.6.9
```

Try not to remove a release other people have already downloaded. If a version turns out to be
broken, it is usually kinder to release a fixed version with a higher number than to make an old
one disappear.

## Test releases

To try a release without it looking like the finished thing, run **Cut a release** with the same
version number and something like `rc1` in the **suffix** box. The tag becomes `v1.6.9-rc1`, and
anything with a suffix is published as a **pre-release**: it appears on the Releases page marked
clearly, and it does not become the version people land on when they visit that page.

This is the way to check that the installers actually build before you cut the real release, and
it is worth doing the first time, or any time the build changes. Delete the test release and its
tag afterwards.

## Doing it by hand

The button is just a workflow around the same tag mechanism as before, so the manual route still
works if you ever need it: update `<version>` in `pom.xml` through a pull request, make sure the
checks passed on `main`, then:

```bash
git tag v1.6.9
git push origin v1.6.9
```

The Release build refuses to run if the tag does not match the pom, or if the tagged commit never
passed the checks, so the worst a mistake can do is a red run in the Actions tab.
