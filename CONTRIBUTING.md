# Contributing

Thank you for your interest in contributing to hibiki.

## Issues

Before opening an issue, please search for similar reports or requests using relevant keywords.

### Bug reports

Please include enough information to reproduce the problem:

- app version and device;
- source, anime title, or episode involved;
- steps to reproduce the problem;
- expected and actual behavior;
- relevant logs, if available.

### Feature requests

Describe the problem you want to solve and how the proposed change would help. Larger features are welcome to be discussed in an issue before implementation.

### New sources

Anime sources are no longer part of this repository. They live in [hibiki-sources](https://github.com/akkirrai1337/hibiki-sources) as separate installable extension modules, built and published by that repo's own CI. Open an issue or pull request there to add or fix a source.

## Pull requests

For a larger change, open or find a related issue first so the approach can be discussed. In the pull request description, explain what was changed, link the related issue, and mention how the changes were tested.

UI changes should include screenshots when useful.

## Building and testing

On Windows, use `gradlew.bat` instead of `./gradlew`.

```shell
./gradlew :parsers:test
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```
