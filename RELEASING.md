# Making a release

A release is a version of the client that other people can download. It appears on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases), and anyone can download it
without a GitHub account.

You create a release by creating a **tag**, which is a label on one commit that says "this is version 1.6.8".

## Before you start

Make sure your changes are already merged into `main` and the checks passed. Look for the green
tick next to the newest commit on the
[main branch](https://github.com/PennStateCS/AFCT-Client/commits/main).

## Step 1: choose the new version number

Look at `pom.xml` near the top:

```xml
<version>1.6.8</version>
```

The version has three numbers. Pick the new one like this:

- Fixed a bug, nothing else changed: increase the **last** number. 1.6.8 becomes 1.6.9.
- Added something new: increase the **middle** number and set the last to zero. 1.6.8 becomes 1.7.0.
- Changed something big, so old habits no longer work: increase the **first** number. 1.6.8 becomes 2.0.0.

## Step 2: update the version in `pom.xml`

Edit that line so it holds your new number, then open a pull request with just that change and
merge it once the checks pass.

This step is easy to forget, and it matters. The version number gets written inside the program
itself, so if you skip it the release would say one thing and contain another. The release refuses
to build if you forget, so nothing bad can happen, but you will have to come back and do it.

## Step 3: get the latest `main` and check it passed

```bash
git checkout main
git pull
```

Then open the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions) and check that the
newest run on `main` finished with a green tick. If it is still running, wait for it.

## Step 4: create the tag and push it

The tag is the version number with a `v` in front of it. For version 1.6.8 the tag is `v1.6.8`.

```bash
git tag v1.6.8
git push origin v1.6.8
```

That is the whole release. You do not need a pull request for a tag.

## Step 5: watch it build

Open the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions). A run called
**Release** starts within a few seconds. It takes roughly ten minutes, because the installers are
built on three different machines.

It builds on three machines at once, one for each thing people download:

| File | For |
| --- | --- |
| `AFCT-Client-1.6.8-windows.exe` | Windows |
| `AFCT-Client-1.6.8-macos-apple-silicon.dmg` | Macs made from late 2020 onwards |
| `AFCT-Client-1.6.8-macos-intel.dmg` | Older Intel Macs |
| `afct-client-v1.6.8.jar` | Anyone who would rather run it with their own Java |

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

## If the release fails

The release checks two things before it builds anything. If either one fails, nothing is
published, so there is nothing to clean up. Open the failed run in the Actions tab and read the
step marked in red.

**"No successful CI run for ... so this tag is not releasable."**

You tagged a commit whose checks never passed, or never ran at all. To fix it:

1. Remove the tag (see below).
2. Make sure the checks pass on `main`.
3. Tag again.

**"Tag v1.7.0 does not match the pom version 1.6.8."**

You skipped step 2, or the number in the tag is not the number in `pom.xml`. To fix it:

1. Remove the tag (see below).
2. Update `<version>` in `pom.xml` to match, through a pull request as usual.
3. Tag again once that is merged.

### Removing a tag

If you tagged the wrong thing, remove the tag and start again:

```bash
git tag -d v1.6.8
git push origin --delete v1.6.8
```

If a release was already published, delete it on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases) as well, or run:

```bash
gh release delete v1.6.8
```

Try not to remove a release other people have already downloaded. If a version turns out to be
broken, it is usually kinder to release a fixed version with a higher number than to make an old
one disappear.

## Test releases

To try a release without it looking like the finished thing, put a dash and a label on the end of
the version. You do **not** need to change `pom.xml` for this: the check only compares the numbers,
so a pom saying 1.6.8 accepts both `v1.6.8` and `v1.6.8-rc1`.

```bash
git tag v1.6.8-rc1
git push origin v1.6.8-rc1
```

Anything with a dash in it is published as a **pre-release**. It appears on the Releases page
marked clearly, and it does not become the version people land on when they visit that page.

This is the way to check that the installers actually build before you cut the real release, and
it is worth doing the first time, or any time the build changes. Delete the test release and its
tag afterwards.
