# jvm-inspector

[![build](https://github.com/ctbot000/jvm-inspector/actions/workflows/build.yml/badge.svg)](https://github.com/ctbot000/jvm-inspector/actions/workflows/build.yml)
[![license](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net)

Report **every detail a JVM will tell you about itself** - its own, another one on the same
machine, or one across a JMX connection - as readable text, JSON or Markdown.

It is a single jar with **no runtime dependencies**, so dropping it onto a class path or into a
container cannot disturb what you are inspecting. `--serve` turns the same jar into a browser
interface with a live dashboard.

```console
$ java -jar jvm-inspector.jar --only overview
====================================================================================================
jvm-inspector 1.0.0
Target:    this JVM (pid 34246)
Generated: 2026-09-22 22:15:57.765 +09:00
====================================================================================================
...
Java version                           26.0.2.1
VM                                     OpenJDK 64-Bit Server VM 26.0.2.1
VM mode                                mixed mode, sharing
Uptime                                 117.0 ms
Operating system                       Mac OS X 27.0 (aarch64)
Available processors                   10
Heap used                              2.06 MiB (2,164,944 bytes)
Heap max                               4.00 GiB (4,294,967,296 bytes)
Heap utilisation                       0.1%
Live threads                           6
Loaded classes                         1,844
Garbage collectors                     G1 Young Generation, G1 Concurrent GC, G1 Old Generation
JIT compiler                           HotSpot 64-Bit Tiered Compilers
```

## What it reports

Twenty-two sections, each one selectable on its own with `--only` or `--skip`:

| Section | What it covers |
| --- | --- |
| `overview` | The headline facts, pulled from the sections below |
| `vm` | Version, specification, launch arguments, class/library/module paths, the `release` file |
| `process` | The OS process, its parent, its children, its command line and CPU time |
| `os` | Every attribute the operating system bean publishes, including the platform specific ones |
| `container` | cgroup limits and the sizing flags the VM derived from them |
| `memory` | Heap, non-heap, every pool with peaks and thresholds, managers, NIO buffer pools |
| `gc` | Every collector, its totals, and a per-pool breakdown of its most recent cycle |
| `threads` | Counts, per-thread state, CPU time and allocation, deadlock detection, stack traces |
| `classes` | Load counts, the loader hierarchy, the class path, class data sharing |
| `jit` | The compiler, time spent, the segmented code cache, the flags that tune it |
| `modules` | The resolved boot layer: requires, exports, opens, uses, provides, per module |
| `flags` | All ~900 `-XX` flags, grouped by origin, with the non-default ones called out |
| `system-properties` | Every property, with path-shaped values broken into their entries |
| `environment` | Environment variables, with secret-looking names masked |
| `filesystem` | The default file system, its stores and their free space, the VM's directories |
| `network` | Interfaces, addresses, MTU and flags, plus the proxy and protocol settings |
| `security` | Providers in preference order, algorithms, TLS defaults, `java.security` values |
| `locale` | Default charset, locale and time zone, and the properties behind them |
| `jfr` | Flight Recorder availability, active recordings, configurations, event types |
| `instrumentation` | Agent-only detail: every loaded class by loader and package, object header sizes |
| `diagnostics` | Every `jcmd` command the target supports, with the read-only ones executed |
| `mbeans` | The whole MBean registry, including whatever the application itself publishes |

## Getting it

Java 17 or newer. Build the jar with the wrapper:

```bash
./gradlew build
```

The jar lands in `build/libs/jvm-inspector-<version>.jar`. It is executable, and it is also a valid
Java agent.

## Using it

### Inspect the JVM you start

```bash
java -jar jvm-inspector.jar
```

### Inspect another JVM on this machine

```bash
java -jar jvm-inspector.jar --list-jvms
java -jar jvm-inspector.jar --pid 34295
```

Attaching starts the target's own management agent on a loopback endpoint and reads it over JMX, so
nothing is installed in the target permanently.

Sections that read the JDK API directly - modules, environment, file system, network, security -
cannot be produced from outside the target and are reported as skipped rather than guessed at.

### Inspect a remote JVM

```bash
java -jar jvm-inspector.jar --jmx app-host:9010
java -jar jvm-inspector.jar --jmx app-host:9010 --jmx-user ops --jmx-password-file ~/.jmx-pass
```

The password is read from a file or from `JVM_INSPECTOR_JMX_PASSWORD`, or prompted for - never from
the command line, so it stays out of shell history and process listings.

### Browse it

```bash
java -jar jvm-inspector.jar --serve          # http://127.0.0.1:7777/
java -jar jvm-inspector.jar --pid 34295 --serve 8080 --open
```

The page is served by the jar itself - one HTML file, no CDN, nothing fetched from the network - so
it works on a machine with no internet access and inside a container with a forwarded port.

- A **live dashboard** polled every two seconds: heap, non-heap, threads, classes, GC, process CPU,
  uptime, a bar per memory pool, and a sparkline per counter for as long as the page stays open.
- **Every section** on its own page, with sortable tables and a filter box that hides
  non-matching rows across the whole section - the fastest way through 900 VM flags.
- **Depth controls in the header** (`detail`, `redact`, stack depth, `expensive`) that re-collect on
  change, and a download menu for the text, JSON or Markdown report.
- Light and dark, and it works at phone width.

The JSON behind it is a plain API if you want to script against it: `/api/live`, `/api/sections`,
`/api/section?id=memory`, `/api/report`, `/api/download?format=markdown`.

**On exposing it.** The server binds to `127.0.0.1` and sends no CORS headers, so another origin
cannot read its answers, and it rejects any request whose `Host` header is not a loopback name,
which is what stops DNS rebinding from turning the port into a public one. There is no
authentication, so `--host 0.0.0.0` hands the target's system properties, command line and stack
traces to anything that can reach the port; prefer an SSH tunnel.

### Watch a JVM instead of photographing it

```bash
$ java -jar jvm-inspector.jar --pid 34295 --watch 1
Watching attached JVM (pid 34295 - Sleeper) every 1s. Press Ctrl-C to stop.
time      heap used   heap max   use%   non-heap threads  daemon   classes +loaded collections  +pause
22:22:26    5.9 MiB  256.0 MiB   2.3%   14.2 MiB      13      12      2674       -        13       -
22:22:27    5.9 MiB  256.0 MiB   2.3%   14.2 MiB      13      12      2674      +0        13     0ms
22:22:28    5.9 MiB  256.0 MiB   2.3%   14.2 MiB      13      12      2674      +0        14     2ms
```

Counters that only grow are printed as the change since the previous sample, because that is the
number worth watching.

### As a Java agent

Running the jar as an agent publishes the instrumentation bean, which is the only way to get the
true loaded class set, the class histogram by loader, and object header sizes:

```bash
java -javaagent:jvm-inspector.jar -jar your-app.jar

# or write a report automatically
java -javaagent:jvm-inspector.jar=dump=exit,format=json,output=/tmp/jvm.json -jar your-app.jar
```

You can also inject the agent into a JVM that is already running:

```bash
java -jar jvm-inspector.jar --pid 34295 --load-agent --only instrumentation
```

```console
Classes by defining loader (4 rows)
Count  Name
-----  ---------------------------------------------------------------
2903   <bootstrap>
8      jdk.internal.loader.ClassLoaders$AppClassLoader (app)
2      jdk.internal.loader.ClassLoaders$PlatformClassLoader (platform)
1      sun.reflect.misc.MethodUtil
```

## Output

```bash
java -jar jvm-inspector.jar -f json -o report.json
java -jar jvm-inspector.jar -f markdown -o report.md
```

JSON keeps raw types - a byte count stays a number - and carries the human rendering alongside it,
so neither a script nor a person has to undo the other's formatting:

```json
{"type": "property", "name": "Heap used", "kind": "bytes",
 "value": 1610612736, "display": "1.50 GiB (1,610,612,736 bytes)"}
```

## Depth and safety

| Option | Effect |
| --- | --- |
| `--detail full` | Every row the JVM will hand over: all flags, all algorithms, all charsets, every module's exports, every MBean attribute value |
| `--expensive` | Also run operations that pause the target, such as `GC.class_histogram` |
| `--stack-depth <n>` | Frames kept per thread (default 12; `0` drops stack traces) |
| `--redact <mode>` | `none`, `secrets` (default), or `all` |

Reports get pasted into tickets, so redaction is on by default: variables and arguments whose
**names** look like credentials are masked, along with a handful of token shapes (AWS keys, GitHub
tokens, Slack tokens, JWTs, PEM private keys). `--redact all` additionally masks the user name, the
home directory, host names, IP addresses and hardware addresses.

Everything the tool runs by default is read-only. The one category that is not free -
`GC.class_histogram`, `Compiler.CodeHeap_Analytics` - is behind `--expensive` and says so.

## All options

```
Target (the default is the JVM the tool itself runs in)
  -p, --pid <pid>              attach to another JVM on this machine
      --jmx <url|host:port>    connect to a JMX endpoint
      --list-jvms              list the JVMs on this machine, then exit
      --load-agent             with --pid, load this jar into the target as an agent first

Output
  -f, --format <fmt>           text (default), json or markdown
  -o, --output <file>          write to a file instead of standard output
      --only <ids>             comma separated section ids to include
      --skip <ids>             comma separated section ids to leave out
      --list-sections          list the section ids, then exit

Depth
  -d, --detail <level>         standard (default) or full
      --full                   the same as --detail full
      --expensive              also run operations that pause the target
      --stack-depth <n>        stack frames per thread (default 12, 0 for none)
      --redact <mode>          none, secrets (default) or all

JMX authentication
      --jmx-user <user>        user name for an authenticated endpoint
      --jmx-password-file <f>  file holding the password

Live view
      --serve [port]           serve the browser interface (default port 7777)
      --port <n>               port for --serve, when not given after it
      --host <address>         interface for --serve (default 127.0.0.1)
      --open                   open the interface in a browser
      --watch <seconds>        print a compact sample line on an interval
      --samples <n>            stop after n samples

Other
  -h, --help / -V, --version
```

## How it works

Every section is an `Inspector` that fills a tree of sections, properties, tables, notes and code
blocks; the renderers turn that one tree into text, JSON or Markdown, and the browser interface
walks the JSON form of the same tree. Inspectors read the target
through a `Target` abstraction that is always an `MBeanServerConnection`, which is why the same code
serves this JVM, an attached one and a remote one. Facts that no management bean exposes come from
the HotSpot diagnostic commands - the set `jcmd` drives - invoked through the
`com.sun.management:type=DiagnosticCommand` bean, so they work remotely too.

A section that fails does not take the report with it: the failure and its stack trace are reported
in place, and the run summary at the end says what succeeded, what was skipped and how long each
section took.

## Development

```bash
./gradlew build          # compile, test, package
./gradlew test           # tests only
./gradlew report         # run the inspector against the Gradle-launched JVM
```

The build targets Java 17 bytecode and is tested on 17, 21 and 25.

## Licence

[Apache License 2.0](LICENSE).
