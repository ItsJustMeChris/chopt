How to do a full release.

Each profile carries its own toolchain settings, and `useMcXXXX` resets any stale ones
(including `org.gradle.java.home` and `jade_version`), so the profiles can be run in any
order with no manual edits to `gradle.properties`.

`mod_version` lives only in `gradle.properties` and is preserved across profile switches
— bump it once, before the first build.

Process:

make .builds folder

gradle useMc1201
gradle deepClean
gradle build
copy output to .builds/

gradle useMc1218
gradle deepClean
gradle build
copy output to .builds/

gradle useMc12110
gradle deepClean
gradle build
copy output to .builds/

gradle useMc12111
gradle deepClean
gradle build
copy output to .builds/

gradle useMc261
gradle deepClean
gradle build
copy output to .builds/

gradle useMc2611
gradle deepClean
gradle build
copy output to .builds/

gradle useMc2612
gradle deepClean
gradle build
copy output to .builds/

gradle useMc262
gradle deepClean
gradle build
copy output to .builds/

gradle useMc263Snapshot7
gradle deepClean
gradle build
copy output to .builds/

Notes:

- 26.1, 26.1.1, 26.1.2, 26.2 and 26.3-snapshot-7 are unobfuscated (no mappings) and
  need a Java 25 Gradle JVM; each profile pins one.
- Jade is an optional compile-time dependency. Every profile pins a `jade_version`
  except 26.3-snapshot-7, where Jade has published no build yet — that profile leaves
  it unset, and `build.gradle` then excludes `**/compat/jade/**` from compilation.
  When Jade ships for 26.3, just set `jade_version` in that profile.

Troubleshooting:

- "The supplied javaHome seems to be invalid" before any task runs means a stale
  Gradle daemon is registered against a JDK that has since been uninstalled.
  Clear it with `./gradlew --stop`, then delete `~/.gradle/daemon/*/registry.bin*`.
