# JCollectd

This project aims to be a Java (relatively) lightweight alternative to more complete but more complex monitoring system like Nagios or collectd. 
It has simplicity on his mind: no external dependency except Java 8, just one Jar file, one property file and one (single file) sqlite database.

The program does the following:
* reads the configuration file
* collects system informations reading `/proc` virtual filesystem on Linux, or via `sysctl` on FreeBSD
* writes collected samples into a [sqlite](https://www.sqlite.org/) database
* (optionally) extracts samples from the last N hours and creates an html report, with nice graphs featured by [Google Charts](https://developers.google.com/chart/)
* rinse and repeat until stopped

## Build and installation
Build is done via [Apache Maven](https://maven.apache.org/)
```bash
$ mvn clean package
```
A single archive file `JCollectd.jar` will be produced into `target/` directory. Copy it whenever you want.

## Configuration
A sample configuration file is included in `artifacts/` directory. Configuration file must be a standard Java property file. 
Supported general options are:
* `dbPath`: the absolute path for sqlite database file (mandatory). The program checks that selected directory exists and it's writable by the current user; if the database file doesn't exists, it is created and initialized with required tables.
* `webPath`: the absolute path for output html report (optional). The program checks that selected directory exists and it's writable by the current user. If not provided, report production phase wil be skipped, in case you prefer a third party tool like [Grafana](https://grafana.com/) to build your dashboard.
* `hostname`: the machine name (optional), used to customize html report. If not provided, the program tries to autodetect it; if it fails, it defaults to `localhost`. Hostname is saved with the timeseries, in case you want to use a single database for multiple servers.
* `reportHours`: number of hours of sampling used to create graphs (optional). If provided, it must be parsable as a positive integer, if not provided defaults to 12.
* `retentionHours`: number of hours of data retention (optional). If provided, can be >= than `reportHours` to keep into database more data than what is shown in the dasboard; defaults to `reportHours` if not provided; if set to a negative values, completely disables deletion of old samples.
* `interval`: number of seconds between samplings (optional). If provided, it must be parsable as a positive integer, if not provided defaults to 60.

After setting general option, you can configure an arbitrary number of probes. Each probe must be defined by a `probe.N.type` property, where N is a progressive number, starting by 1.
Each probe definition can have one of the following values:
* `load`: enables the average load sampling
* `cpu`: enables the CPU percent utilization sampling
* `mem`: enables the memory, swap and cache sampling
* `net`: enables the network traffic sampling
* `disk`: enables the hard disk usage sampling
* `zfs`: enables the ZFS zpool usage sampling (currently on FreeBSD only)
* `gpu`: enables the GPU usage sampling (currently on Linux only, with Nvidia cards, and `nvidia-smi` is assumed to be installed)

Each probe can have an optional property `probe.N.size` which can assume the following values:
* `full`: for a full width chart (which is the default)
* `half`: for a (guess what!) half width chart

The produced report is fully HTML5 compliant, and features a flowing, responsive layout. You can play with with progressive numbers and sizes to produce your ideal layout. To be mobile-friendly, graphs will be all drawn at full width on smaller resolution devices.

Probes `net`, `disk` and `zfs` needs an additional mandatory property to be defined: `probe.N.device`, with the name of the network interface, the block device or the ZFS zpool you want to be sampled, (e.g. `probe.4.device=eth0`). The optional parameter `probe.N.label` can be used to see a more meaningful name in the report.

`disk` probe supports the aggregation of devices, with a `+` separated list of devices to be aggregated.\
Linux provides I/O totals for `mdadm` raid arrays, so you have the choice to probe the *logical* amount of disk activity (using the array itself as device, e.g. `probe.5.device=md0`), or the aggregation of single disks composing the array (e.g. `probe.5.device=sda+sdb+sdc` in case you have a 3-disk RAID5).\
FreeBSD, on the other hand, provides totally different mechanisms to retrieve the two types of readings, so you can opt for a specific `zfs` probe to get the first, or a standard aggregated `disk` probe for the latter (e.g. `probe.5.device=da0+da1+da2` in case you have a 3-disk raidz1).

There is a simple sanity check on probe configuration (for example on mandatory parameters, and probe numbering), but I am sure you can shoot yourself in the leg if you try enough.

## Usage
Simply execute the jar in background, with a line like this (assuming `/jdk11` is your `$JAVA_HOME`)
```
nohup /jdk11/bin/java -Xms32m -Xmx32m -jar JCollectd.jar jcollectd.properties > jcollectd.log &
```
The output report will be produced at the location specified in configuration file, ready to be served by the httpd daemon of your choice. The result will be something like this:

![report](https://raw.githubusercontent.com/GilGalaad/JCollectd/master/artifacts/JCollectd.png)

## Run as service
In the `etc` folder you will find examples to run JCollectd as OS service, in `rc` and `s` there are simple rc script and systemd unit files for integration with FreeBSD and Linux init system, respectively.

## Notes
Worths to say that:
* the memory footprint is relatively low, a few MB of heap size is enough to run the daemon with a reasonable configuration, but you may want to raise maximum heap size to alleviate pressure on garbage collection (depending on your dataset size).
* even with a very low heap, some memory will be consumed by internal mechanisms of sqlite memory allocation, this will be native memory and cannot be tuned via Java parameters.
* logging facility is provided by [log4j2](https://logging.apache.org/log4j/2.x/). The program by default logs only on the console (at `INFO` level) a brief recap of what has been parsed from configuration file during startup, and during shutdown; and any unrecoverable error (at `FATAL` level) that will prevent a correct monitoring, causing the program to exit. So there is no need to rotate log file (which is actually impossible with `logrotate` because Java ignores HUP signals), a single log file will be enough to discover if something is going wrong, and why. 
* If you feel brave enough, you can raise the log level in `log4j2.xml` configuration file, and let the framework rotate the logs foy rou, with a [RollingFileAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html#RollingFileAppender)
* there is no need for a complex init script. You can start the daemon with the provdided command at boot, and a proper signal handler will ensure a clean shutdown in case of a `KILL` signal.

## TODO
* ~~Add support for FreeBSD platform~~ Done
* ~~Make the sampling rate (currently 60 seconds) user configurable~~ Done
* Add support for more RDBMS, like [PostgreSQL](https://www.postgresql.org/)
* ~~Add GPU support~~ Done (on Linux only)

#### Contributions, critics, suggestions are always welcome. Cheers!
