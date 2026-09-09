# AFCT Client

This repository contains the desktop client for AFCT.

Related repositories:

- [AFCT Dashboard](https://github.com/PennStateCS/AFCT)
- [AFCT Evaluator](https://github.com/PennStateCS/AFCT-Evaluator)

## Build and test it yourself

You need Java 21 and Maven.

```bash
mvn verify
```

That compiles the code, runs the tests, and puts the finished program in `target/`. The file you
can run is `target/afct-client.jar`.

The same command runs automatically on every pull request. If it fails on your machine, it will
fail there too.

## Making a release

A release is a version of the client that other people can download. It appears on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases), and anyone can download it
without a GitHub account.

Releases do not happen on their own. Merging to `main` does not make one. You make a release by
creating a **tag**, which is a label on one commit that says "this is version 1.6.8".

### Before you start

Make sure your changes are already merged into `main` and the checks passed. Look for the green
tick next to the newest commit on the
[main branch](https://github.com/PennStateCS/AFCT-Client/commits/main).

### Step 1: choose the new version number

Look at `pom.xml` near the top:

```xml
<version>1.6.8</version>
```

The version has three numbers. Pick the new one like this:

- Fixed a bug, nothing else changed: increase the **last** number. 1.6.8 becomes 1.6.9.
- Added something new: increase the **middle** number and set the last to zero. 1.6.8 becomes 1.7.0.
- Changed something big, so old habits no longer work: increase the **first** number. 1.6.8 becomes 2.0.0.

### Step 2: update the version in `pom.xml`

Edit that line so it holds your new number, then open a pull request with just that change and
merge it once the checks pass.

This step is easy to forget, and it matters. The version number gets written inside the program
itself, so if you skip it the release would say one thing and contain another. The release refuses
to build if you forget, so nothing bad can happen, but you will have to come back and do it.

### Step 3: get the latest `main` and check it passed

```bash
git checkout main
git pull
```

Then open the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions) and check that the
newest run on `main` finished with a green tick. If it is still running, wait for it.

### Step 4: create the tag and push it

The tag is the version number with a `v` in front of it. For version 1.6.8 the tag is `v1.6.8`.

```bash
git tag v1.6.8
git push origin v1.6.8
```

That is the whole release. You do not need a pull request for a tag.

### Step 5: watch it build

Open the [Actions tab](https://github.com/PennStateCS/AFCT-Client/actions). A run called
**Release** starts within a few seconds and takes about a minute.

When it finishes, your new release is on the
[Releases page](https://github.com/PennStateCS/AFCT-Client/releases) with the program attached and
a list of what changed since last time.

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

To share something for testing without it looking like the finished thing, put a dash and a label
on the end of the version:

```bash
git tag v1.7.0-rc1
git push origin v1.7.0-rc1
```

Anything with a dash in it is published as a **pre-release**. It appears on the Releases page
marked clearly, and it does not become the version people land on when they visit that page.
