# AFCT Client

This repository contains the desktop client for AFCT.

Related repositories:

- [AFCT Dashboard](https://github.com/PennStateCS/AFCT)
- [AFCT Evaluator](https://github.com/PennStateCS/AFCT-Evaluator)

## Build and test it

You need Java 21 and Maven.

```bash
mvn verify
```

That compiles the code, runs the tests, and puts the finished program in `target/`. The file you
can run is `target/afct-client.jar`.

The same command runs automatically on every pull request. 

## Making a release

Pushing a version tag publishes a downloadable release. See [RELEASING.md](RELEASING.md) for the
steps.
