# DataStack (Paper plugin, Minecraft 26.3)

Merges nearby same-kind mobs and dropped items into one entity and shows the
total above it as `xN`, allowing far more than 64.

## Build
Needs a Paper server install for the classpath (no Gradle):

    ./build.sh /path/to/paper-server-dir   # folder containing libraries/ and versions/

Runs the unit tests (JUnit console jar is fetched into `lib/` and checksum-verified),
then writes `build/DataStack.jar`. Drop it in `plugins/`.

## Config
`mob-radius`, `item-radius`, `max-mob-stack`, `max-item-stack` in `config.yml`.
