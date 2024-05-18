# JCollectd

This project aims to be a Java (relatively) lightweight alternative to more complete but more complex monitoring system like Nagios or collectd. 
It has simplicity on his mind: no external dependency except Java 21, just one Jar file, one configuration file and one (single file) sqlite database.

The program does the following:
* reads the configuration file
* collects system informations reading `/proc` virtual filesystem on Linux, or via `sysctl` on FreeBSD
* writes collected samples into a [sqlite](https://www.sqlite.org/) database
* provides a simple embedded [Angular](https://angular.io/) web application, featuring nice reports made with [Apache ECharts](https://echarts.apache.org/en/index.html)
* rinse and repeat until stopped

![immagine](https://github.com/GilGalaad/JCollectd/assets/18036990/73ff1916-b9ab-4f7c-b48d-212225c74955)

## Build and installation
Build is done via [Apache Maven](https://maven.apache.org/)
```bash
$ mvn clean package
```
A single file `jcollectd.jar` will be produced into `target/` directory. Copy it whenever you want.

## Configuration
Configuration is done via single YAML file. 
Supported parameters are:

| Parameter | Mandatory | Defaut value | Description |
| --- | --- | --- | --- |
| `verbose` | no | false | Boolean value to set log verbosity. Normally the application will print its configuration when starting, and fatal errors when they occur, in verbose mode will print all debug lines. |
| `hostname`| no | autodetect | The machine name, used to customize html report. If not provided, the program tries to autodetect it; if it fails, it falls back to `localhost` |
| `interval`| no | PT1M | Interval between samplings, in case you want to customize the granularity, but one minute is a safe and sane default. Expressed as [Duration](https://docs.oracle.com/en%2Fjava%2Fjavase%2F21%2Fdocs%2Fapi%2F%2F/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) |
| `retention`| no | PT12H | Time window of data to keep in the database and to draw charts. Expressed as [Duration](https://docs.oracle.com/en%2Fjava%2Fjavase%2F21%2Fdocs%2Fapi%2F%2F/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) |
| `port`| no | 8080 | HTTP port to bind the webserver to |
| `probes`| yes | - | List of defined probes. You must define at least one probe of the program will refuse to start |

Each probe can be confiured by the following parameters:
| Parameter | Mandatory | Defaut value | Description |
| --- | --- | --- | --- |
| `type` | yes | - | The probe type, can have one of the following values: <ul><li>`load`: enables the average load sampling</li> <li> `cpu`: enables the CPU percent utilization sampling</li> <li> `mem`: enables the memory, swap and cache sampling</li> <li> `net`: enables the network traffic sampling</li> <li> `disk`: enables the hard disk usage sampling</li> <li> `zfs`: enables the ZFS dataset usage sampling (currently on FreeBSD only)</li> <li> `gpu`: enables the GPU usage sampling (currently with Nvidia cards only, and `nvidia-smi` is assumed to be installed)</li></ul> |
| `size` | no | `full` | The chart size, can be full or half page width |
| `device` | yes | - | Probes `net`, `disk` and `zfs` require the device you want to monitor, respectively the name of the network interface, or the block device, or the ZFS dataset. This parmeter is ignored for other probe types |
| `label` | no | the value of `device` paremeter | Used to customize when you want to customize the device name shown in the chart, with a more meaningful value (e.g. `LAN` and `WAN` instead of `eth0` and `eth1`) |

#### Additional informations
`disk` probe supports the aggregation of devices, with a `+` separated list of devices to be aggregated, in case of soft RAID arrays or ZFS pools.\
Linux provides I/O totals for `mdadm` raid arrays, so you have the choice to probe the *logical* amount of disk activity (using the array itself as device, e.g. `device: md0`), or the aggregation of single disks composing the array (e.g. `device: sda+sdb+sdc` in case you have a 3-disk RAID5).\
FreeBSD, on the other hand, provides totally different mechanisms to retrieve the two types of readings, so you can opt for a specific `zfs` probe to get the first, or a standard aggregated `disk` probe for the latter. Notice that ARC is involved in the calculation returned by the kernel, so if you read a 1GB that is totally in cache, `zfs` probe will report the reading, `disk` will not.

#### Sample configuration file
```yaml
retention: PT24H
probes:
  - type: load
    size: full
  - type: cpu
    size: half
  - type: mem
    size: half
  - type: net
    size: half
    device: ix0
  - type: disk
    size: half
    device: ada0+ada1+ada2+ada3+ada4+ada5
    label: zstore
```

## Usage
Simply execute the jar in background, with a command like this (assuming java binary is in PATH)
```
nohup java -Xms32m -Xmx32m -jar jcollectd.jar config.yaml > jcollectd.log &
```

## Run as service
In the `artifacts/service` folder you will find examples to run JCollectd as OS service, in `rc` and `systemd` there are simple rc script and systemd unit files for integration with FreeBSD and Linux init system, respectively.

## Notes
Worths to say that:
* The memory footprint is relatively low, a few MB of heap size is enough to run the daemon with a reasonable configuration, but you may want to raise maximum heap size (depending on your dataset size).
* Even with a very low heap, some memory will be consumed by internal mechanisms of sqlite memory allocation, this will be native memory and cannot be tuned via Java parameters.
* Logging facility is provided by [log4j2](https://logging.apache.org/log4j/2.x/). The program by default logs only on the console (at `INFO` or `DEBUG` level depending on chosen verbosity) a brief recap of what has been parsed from configuration file during startup, and any unrecoverable error that will prevent a correct monitoring, causing the program to exit. So there is no need to rotate log file (which is actually impossible with `logrotate` because Java ignores HUP signals), a single log file will be enough to discover if something is going wrong, and why.

#### Contributions, critics, suggestions are always welcome. Cheers!
