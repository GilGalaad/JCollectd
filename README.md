# JCollectd

This project aims to be a Java (relatively) lightweight alternative to more complete but more complex monitoring system like Nagios or collectd. 
It has simplicity on his mind: no dependencies except Java 8, just one Jar file, one property file and one (single file) sqlite database.

The program does the following:
* reads the configuration file
* collects system informations reading `/proc` virtual filesystem on Linux, or via `sysctl` on FreeBSD
* writes collected samples into a [sqlite](https://www.sqlite.org/) database
* extracts samples from the last N hours and creates an html report, with nice graphs featured by [Google Charts](https://developers.google.com/chart/)
* rinse and repeat until stopped

## Build and installation
Build is done via [Apache Ant](http://ant.apache.org/)
```bash
$ ant -f build-jar.xml clean dist
```
A single file `JCollectd.jar` will be produced into `dist/` directory. Copy it whenever you want.
Original `build.xml` file and `nbproject/` directory are provided, in case you want to open the project in [Netbeans IDE](https://netbeans.org/).

## Configuration
A sample configuration file is included in `artifacts/` directory. Configuration file must be a standard Java property file. 
Supported general options are:
* `dbpath`: the absolute path for sqlite database file (mandatory). The program checks that selected directory exists and it's writable by the current user; if the database file doesn't exists, it is created and initialized with required tables.
* `webpath`: the absolute path for output html report (mandatory). The program checks that selected directory exists and it's writable by the current user.
* `hostname`: the machine name (optional), used to customize html report. If not provided, the program tries to autodetect it; if it fails, it defaults to `localhost`.
* `reportHours`: number of hours of sampling used to create graphs (optional). If provided, it must be parsable as a positive integer, if not provided defaults to 12.
* `retentionHours`: number of hours of data retention (optional). If provided, it must be parsable as a positive integer and must >= than `reportHours`, otherwhise defaults to `reportHours`.
* `interval`: number of seconds between samplings (optional). If provided, it must be parsable as a positive integer, if not provided defaults to 60.

After setting general option, you can configure an arbitrary number of probes. Each probe must be defined by a `probe_N_type` property, where N is a progressive number, starting by 1.
Each probe definition can have one of the following values:
* `load`: enables the Average Load sampling
* `cpu`: enables the Cpu percent utilization sampling
* `mem`: enables the Memory & Swap sampling
* `net`: enables the Network Traffic sampling
* `hdd`: enables the Hard Disk usage sampling

Each probe can have an optional property `probe_N_size` which can assume the following values:
* `full`: for a full width chart (which is the default)
* `half`: for a (guess what!) half width chart

The produced report is fully HTML5 compliant, and features a flowing, responsive layout. You can play with with progressive numbers and sizes to produce your ideal layout. To be mobile-friendly, graphs will be all drawn at full width on smaller resolution devices.

Probes `net` and `hdd` needs an additional mandatory property to be defined: `probe_N_device`, with the name of the network interface or the block device you want to be sampled, (e.g. `probe_5_device=eth0`).

`hdd` probe supports the aggregation of devices, with an undescore separated list of devices to be aggregated (e.g. `probe_5_device=sda_sdb_sdc` in case you have a 3-disk RAID5).
This is done mainly because while Linux provides I/O totals for `mdadm` raid arrays, FreeBSD provides I/O bandwidth but not totals for ZFS `zpools`. So we must go a bit lower level and do some math.

There is a simple sanity check on probe configuration (for example on mandatory parameters, and probe numbering), but I am sure you can shoot yourself in the leg if you try enough.

## Usage
Simply execute the jar in background, with a line like this (assuming `/jdk1.8` is your `$JAVA_HOME`)
```
nohup /jdk1.8/bin/java -XX:MaxMetaspaceSize=10m -Xms6m -Xmx6m -jar JCollectd.jar jcollectd.properties > jcollectd.log &
```
The output report will be produced at the location specified in configuration file, ready to be served by the httpd daemon of your choice. The result will be something like this:
![report](https://raw.githubusercontent.com/GilGalaad/JCollectd/master/artifacts/JCollectd.png)

Worths to say that:
* the memory footprint is as low as can be, 6 MB of heap size (which is the minimum allowed by Java) is enough to let the daemon run with a reasonable configuration, but you may want to limiting maximum heap size to something more (like 32m) to alleviate pressure on garbage collection.
* even with a very low heap, some memory will be consumed by internal mechanisms of sqlite memory allocation, this will be native memory and cannot be tuned via Java parameters.
* the program logs only on the console (at `INFO` level) a brief recap of what has been parsed from configuration file during startup, and during shutdown; and any error (at `SEVERE` level) that will prevent a correct monitoring, causing the program to exit. So there is no need to rotate log file (which is actually impossible with `logrotate` because Java ignores HUP signals), a single log file will be enough to discover if something is going wrong, and why. Of course you can modify the desired log level in the main class.
* there is no need for a complex init script. You can start the daemon with the provdided command at boot, and a proper signal handler will ensure a clean shutdown in case of a `KILL` signal.

## TODO
* ~~Add support for FreeBSD platform.~~ Done.
* ~~Make the sampling rate (currently 60 seconds) user configurable.~~ Done.

####Contributions, critics, suggestions are always welcome. Cheers!
