How to do a full release. 

Process:

make .builds folder

gradle useMc261Snapshot1
gradle deepClean
gradle build
copy output to .builds/

> remove org.gradle.java.home=C\:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot from gradle.properties
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