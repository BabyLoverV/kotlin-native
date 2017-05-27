# Samples

This directory contains a set of samples demonstrating how one can work with Kotlin/Native. The samples can be
built using either command line tools (via `build.sh` script presented in each sample directory) or using a gradle build.

**Note**: If the samples are built from a source tree (not from a distribution archive) the compiler built from the
sources is used. So one need to build the compiler and the stub generator first
(see [README.md](https://github.com/JetBrains/kotlin-native/blob/master/README.md) for details).

See `README.md` in sample directories to learn more about specific samples and the building process.

One may also build all the samples with one command. To build them using the command line tools run:

    ./build.sh
    
To build all the samples using the gradle build:

    ./gradlew build
    
One also may launch the command line build via a gradle task `buildSh` (equivalent of `./build.sh` executing):

    ./gradlew buildSh

If the samples are built from a source tree (not from a distribution archive) one may use the Kotlin/Native gradle
plugin built from the sources instead of a released one:

    ./gradlew build --include-build ../    