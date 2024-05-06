# How to contribute

Thank you for considering to contribute to this project to make it the best it can be.

To make this process as frictionless as possible please read through the information in this document.

First up, check existing [issues](https://github.com/MaxMaeder/DriveBackupV2/issues) and [pulls](https://github.com/MaxMaeder/DriveBackupV2/pulls), maybe it has already been submitted or someone else is already working on it, or if you want to find something to contribute to.

## Non Code Contributions

For contributing to translations see [translations/README.md](translations/README.md)

If you found a bug or want to suggest a new feature see [issues/new/choose](https://github.com/MaxMaeder/DriveBackupV2/issues/new/choose) pick an option and fill out the relevant info

## Code Contributions

### Java

The plugin is build using Maven, to build the plugin use `mvn package`. Build artifacts can be cleaned with `mvn clean`.

We recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/) as an IDE, but others like [VSCode](https://code.visualstudio.com/) or [Eclipse IDE](https://eclipseide.org/) should work fine.
TODO recommended run/debug configuration?

TODO specify format-ter/ing used

TODO specify style guide used

TODO specify coding conventions not covered by or different from style guide

We target Java 8 *(JVM)* to support older versions of Minecraft
TODO citation needed

Kotlin is not accepted into the codebase
TODO reason needed

Each of your commits should leave the plugin in a working state.

Before submitting your changes make sure to test them using the latest LTS JRE.
If you need to test changes that affect an uploader test with a backup >= 5GB where possible.

When submitting your change specify what you tested, especially if you could not test all affected components.

### JS

TODO Everything Authenticator related

