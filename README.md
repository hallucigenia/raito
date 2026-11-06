# Raito

Raito is an ambient-light screen grabber for **Android TV and Google TV** that runs on the
TV itself. It is started over ADB as the ordinary shell user. It screenshots the physical
display, scales it to about 160×90 and sends frames straight to a
[Hyperion](https://github.com/hyperion-project/hyperion.ng) server on your network. No root,
no app install, no PC relaying video.

Why: grabbers built on MediaProjection, and scrcpy-style virtual displays, often go black on
a TV's **HDMI input** because the video sits on a separate hardware layer. A physical-display
screenshot taken from the shell user can include that layer, depending on the TV's SoC and
firmware. Raito's `probe` command tells you in a few seconds whether your TV does.

> **Status: early.** It has been verified on one TV so far. Please run `probe` on yours and
> share the result. That report is the most useful contribution right now.

## How it works

```
Android TV (ADB shell user)                        LAN
┌──────────────────────────────────────────┐
│ app_process → Raito                       │   FlatBuffers   ┌──────────┐   e.g. DDP   ┌──────┐
│   physical-display screenshot, 160×90 RGB │ ──────────────▶ │ Hyperion │ ───────────▶ │ LEDs │
│   pauses while the screen is off          │   :19400        └──────────┘              └──────┘
└──────────────────────────────────────────┘
```

* Every frame waits for Hyperion's acknowledgement. Frames expire after 1.5 s by default, so
  the LEDs don't stay frozen on an old frame if Raito dies.
* Lost connections are retried forever, starting at 250 ms and backing off to every 30 s.
  Power, capture and Hyperion keep separate schedules. Restarting Hyperion does not kill Raito.
* While the screen is off, the connection is released and nothing is captured. No wake lock is
  held. A screensaver counts as on.
* Secure (DRM) app layers are excluded by Android, so those apps show black.

## Compatibility

Raito needs a TV that runs **Android TV or Google TV**, with ADB debugging available. TVs on
other systems cannot run it, including Samsung (Tizen), LG (webOS) and Roku.

| Device | Android | Backend | HDMI capture | Notes |
| --- | --- | --- | --- | --- |
| Xiaomi MiTV-MFTR0 | 11 (API 30) | surface | ✅ SDR, HDR10, TV-led Dolby Vision | ~12 fps at 160×90, ~65 ms per capture |

The fast `surface` backend currently uses the Android 10/11 hidden `SurfaceControl` methods.
Android 12 and later renamed them, and there it reports itself unsupported until a matching
backend is added. `screencap` is a slow fallback that works more widely. `probe` shows which
backends work and how fast they are.

**Servers:** tested with Hyperion.ng 2.x. HyperHDR and other FlatBuffers servers are untested.
Raito expects Hyperion's registration reply and its FlatBuffers priority range (100–199), so
another server may refuse the connection as is. Reports are welcome.

## Quick start

Requirements:

* ADB debugging enabled in the TV's Developer options.
* `adb` (Android platform-tools) on your computer.
* A POSIX shell for `bin/raito` (and `build.sh` if you build from source). macOS and Linux work
  as is. On Windows, use WSL or Git Bash.
* A Hyperion instance with the FlatBuffers server enabled (default port 19400).

Connect to the TV over the network first and accept the authorization prompt on screen:

```sh
# TVs with a network / ADB-over-network debugging switch (fixed port 5555):
adb connect 192.168.1.20:5555

# Android 11+ "Wireless debugging": pair once with the pairing code and port from
# "Pair device with pairing code", then connect to the port shown on the Wireless debugging
# screen. The two ports differ and can change when debugging is toggled.
adb pair 192.168.1.20:37099
adb connect 192.168.1.20:41235
```

Then download `raito-<version>.tar.gz` and `SHA256SUMS` from
[Releases](https://github.com/hallucigenia/raito/releases), install and try it:

```sh
shasum -a 256 -c SHA256SUMS --ignore-missing   # or: sha256sum -c SHA256SUMS --ignore-missing
tar xzf raito-<version>.tar.gz && cd raito-<version>
bin/raito install raito.jar

bin/raito probe                     # play moving HDMI video while this runs
cp config/raito.conf.example raito.conf   # set host= to your Hyperion
bin/raito config raito.conf
bin/raito enable                    # allow running and start now
bin/raito report
```

To build from source instead, run `./build.sh` in a clone and install `build/raito.jar` (see
[Development](#development)).

With more than one ADB device, add `-s SERIAL` or set `ANDROID_SERIAL`.

## Commands

| Command | Effect |
| --- | --- |
| `install JAR` | Push the build as `jars/raito-<sha12>.jar`, verify its digest, update the launcher. The running process keeps its build until `restart`. |
| `config FILE` | Push `raito.conf`. Apply it with `restart`. |
| `enable` / `disable` | Create or remove the `enabled` marker, then start or stop. `start` does nothing while disabled. |
| `start` / `stop` / `restart` | Control the process. `start` returns once the process holds its lock, or prints the log if it exits. It never wakes the screen and is a no-op while running. |
| `status` / `log [N]` | Process state and recent log lines. |
| `health` | `healthy pid=… age=…`, `stale`, `unhealthy`, `standby` or `disabled`. |
| `report` | One JSON line: enabled, awake, running, healthy, heartbeat age, measured and target fps. |
| `probe [--backend …] [--frames N]` | Try each backend locally and print a JSON compatibility report. It never contacts Hyperion. |
| `uninstall` | Stop and delete `/data/local/tmp/raito`. |

`healthy` means a frame was acknowledged by Hyperion within the last 10 seconds. The heartbeat
is only refreshed by acknowledged frames, never by retries. A supervisor can use it to decide
whether to switch on LED output.

## Restarting after a TV reboot

Without root, nothing on the TV can start a shell-user process at boot. Something with ADB
access must run `start` again, such as the Home Assistant Android Debug Bridge integration, a
cron job on the Hyperion host (`adb connect … && bin/raito start`), or Shizuku. `start` is
safe to call repeatedly. It respects `disable` and does nothing in standby.

## Security notes

* **Network ADB is powerful.** Any authorized key gets full shell access to the TV. Authorize
  only your own machines, and keep the TV on a trusted network or VLAN.
* Frames travel unencrypted over the LAN. They are tiny, but they are your screen.
* Everything lives in `/data/local/tmp/raito`, owned by the shell user. `uninstall` removes it.

## Configuration

See [`config/raito.conf.example`](config/raito.conf.example). Keys: `host` (required), `port`,
`priority` (100–199 for Hyperion 2.x), `origin`, `width`, `fps`, `backend`
(`surface` | `screencap`), `frame_duration_ms`. Unknown keys are rejected so typos fail loudly.

## Development

```sh
./build.sh test    # compile and run host tests (JDK only)
./build.sh         # tests + DEX jar; needs D8, R8_JAR, or ANDROID_HOME with build-tools
./build.sh dist    # tests + jar + build/dist/{raito-<version>.tar.gz, raito-<version>.jar, SHA256SUMS}
```

CI builds every push and pull request with JDK 17 and a pinned, checksum-verified R8 8.3.37
(see `.github/actions/setup-build`), and uploads the `dist` output as a workflow artifact.

**Releasing:** set `VERSION` in `Main.java`, commit, then push a matching tag such as `v0.2.0`.
The Release workflow refuses a tag that differs from `VERSION`. It runs the tests and publishes
the tarball, the jar and `SHA256SUMS`. Versions `0.x` and those with a suffix are marked as
pre-releases.

The code targets Java 8 with `--min-api 26` and no desugaring, so main sources avoid lambdas.
Android classes are only reached through reflection, so everything except the capture backends
and `DisplayPower` runs and is tested on a normal JVM. Tests use a tiny built-in runner
(`src/test/java/io/github/hallucigenia/raito/TestRunner.java`) to keep the build dependency-free.

| Path | Role |
| --- | --- |
| `src/main/java/io/github/hallucigenia/raito/Forwarder.java` | Capture → send loop, standby, backoff |
| `HyperionProtocol.java`, `HyperionClient.java` | Hand-encoded FlatBuffers and the acknowledged connection |
| `SurfaceCapture.java`, `ScreencapCapture.java` | Capture backends |
| `Probe.java`, `FrameStats.java` | Compatibility report |
| `device/raito.sh` | On-device launcher (enable flag, pid ownership, health, report) |
| `bin/raito` | Host-side install and command forwarding |

## License

Copyright 2026 [hallucigenia](https://github.com/hallucigenia). Licensed under the
[Apache License 2.0](LICENSE); see also [NOTICE](NOTICE). Hyperion's FlatBuffers schema, which
the protocol encoder follows, is part of the MIT-licensed hyperion.ng project. No Hyperion code
is included.
