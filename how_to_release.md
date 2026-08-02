How to do a full release.

Each profile now carries its own toolchain settings, and `useMcXXXX` resets any
stale ones (including `org.gradle.java.home`), so the profiles can be run in any
order with no manual edits to `gradle.properties`.

Process:

make .builds folder

gradle useMc2611
gradle deepClean
gradle build
copy output to .builds/

gradle useMc1218
gradle deepClean
gradle build
copy output to .builds/

gradle useMc12111
gradle deepClean
gradle build
copy output to .builds/

gradle useMc12110
gradle deepClean
gradle build
copy output to .builds/

gradle useMc1201
gradle deepClean
gradle build
copy output to .builds/

Troubleshooting:

- "The supplied javaHome seems to be invalid" before any task runs means a stale
  Gradle daemon is registered against a JDK that has since been uninstalled.
  Clear it with `./gradlew --stop`, then delete `~/.gradle/daemon/*/registry.bin*`.
