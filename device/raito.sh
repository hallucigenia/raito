#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Raito device launcher. Runs as the ordinary ADB shell user, never as root.
# Installed by bin/raito as /data/local/tmp/raito/raito.sh; every file it
# touches lives in that directory.
dir=/data/local/tmp/raito
main=io.github.hallucigenia.raito.Main
conf="$dir/raito.conf"
log="$dir/raito.log"
health_max_age=10
log_max_bytes=1048576
start_wait_tenths=50
stop_wait_tenths=30

usage() {
    echo 'Usage: raito.sh start|stop|restart|enable|disable|status|health|report|log [N]|probe [ARGS]|version' >&2
    exit 2
}

# Screen on, including a screensaver; matches PowerManager.isInteractive() used by the Java side.
screen_awake() {
    dumpsys power | grep -E 'mWakefulness=(Awake|Dreaming)' >/dev/null
}

# Sets $pid when the pid file names a live Raito process (the Java lock holder writes it).
owned_pid() {
    pid=$(cat "$dir/pid" 2>/dev/null)
    case "$pid" in '' | *[!0-9]*) return 1 ;; esac
    test -r "/proc/$pid/cmdline" || return 1
    cmdline=$(tr '\000' ' ' <"/proc/$pid/cmdline")
    case "$cmdline" in "app_process / $main run "*) return 0 ;; *) return 1 ;; esac
}

# Sets $jar from the install pointer, accepting only files inside our jars directory.
current_jar() {
    jar=$(cat "$dir/current" 2>/dev/null)
    case "$jar" in
        *..*) echo 'invalid jar pointer; reinstall' >&2; return 1 ;;
        "$dir"/jars/raito-*.jar) ;;
        *) echo 'invalid jar pointer; reinstall' >&2; return 1 ;;
    esac
    test -f "$jar" || { echo "missing $jar; reinstall" >&2; return 1; }
}

# Sets $age to seconds since the last acknowledged-frame status write.
heartbeat_age() {
    age=-1
    last=$(stat -c %Y "$dir/status" 2>/dev/null) || return 1
    age=$(($(date +%s) - last))
}

status_value() {
    sed -n "s/^$1=//p" "$dir/status" 2>/dev/null | head -n 1
}

# Last occurrence wins and "key value" is accepted, like java.util.Properties.
config_value() {
    sed -n "s/^[[:space:]]*$1[[:space:]]*[=:[:space:]][[:space:]]*//p" "$conf" 2>/dev/null | tail -n 1
}

# Prints $1 if it is a valid JSON number made of digits and at most one inner dot, else $2 (or 0).
number() {
    case "$1" in
        '' | *[!0-9.]* | .* | *. | *.*.* | 0[0-9]*) echo "${2:-0}" ;;
        *) echo "$1" ;;
    esac
}

rotate_log() {
    size=$(stat -c %s "$log" 2>/dev/null || echo 0)
    if [ "$size" -gt "$log_max_bytes" ]; then mv "$log" "$log.1"; fi
}

start_capture() {
    test -f "$dir/enabled" || { echo disabled; return 0; }
    # Never wake the screen just to start capture.
    screen_awake || { echo standby; return 0; }
    owned_pid && { echo "running pid=$pid"; return 0; }
    test -f "$conf" || { echo "missing $conf; push one with: raito config FILE" >&2; return 1; }
    current_jar || return 1
    rotate_log
    # A second concurrent start is harmless: the loser fails the Java lock and exits.
    CLASSPATH="$jar" nohup app_process / "$main" run --config "$conf" --state-dir "$dir" \
        </dev/null >>"$log" 2>&1 &
    spawned=$!
    # Return only once the lock holder has written its pid, so an immediate stop finds it.
    i=0
    while [ "$i" -lt "$start_wait_tenths" ]; do
        if owned_pid; then echo "started pid=$pid"; return 0; fi
        test -d "/proc/$spawned" || break
        sleep 0.1
        i=$((i + 1))
    done
    echo 'raito did not start; recent log:' >&2
    tail -n 5 "$log" >&2
    return 1
}

stop_capture() {
    owned_pid || { echo stopped; return 0; }
    kill -TERM "$pid"
    i=0
    while [ -d "/proc/$pid" ] && [ "$i" -lt "$stop_wait_tenths" ]; do
        sleep 0.1
        i=$((i + 1))
    done
    if [ -d "/proc/$pid" ]; then echo "pid $pid did not stop" >&2; return 1; fi
    echo stopped
}

show_status() {
    if owned_pid; then
        echo "running pid=$pid"
        tail -n 3 "$log"
    elif test -f "$dir/enabled"; then
        echo stopped
        tail -n 3 "$log" 2>/dev/null
    else
        echo disabled
    fi
}

health() {
    if ! test -f "$dir/enabled"; then
        echo disabled
    elif ! screen_awake; then
        echo standby
    elif ! owned_pid; then
        echo unhealthy
    elif heartbeat_age && [ "$age" -ge 0 ] && [ "$age" -le "$health_max_age" ]; then
        echo "healthy pid=$pid age=$age"
    else
        echo stale
    fi
}

report() {
    enabled=false awake=false running=false healthy=false age=-1 fps=0 frames=0 capture_ms=0
    target_fps=$(number "$(config_value fps)" 12)
    test -f "$dir/enabled" && enabled=true
    screen_awake && awake=true
    if owned_pid; then
        running=true
        if heartbeat_age; then
            fps=$(number "$(status_value fps)")
            frames=$(number "$(status_value frames)")
            capture_ms=$(number "$(status_value capture_ms)")
            target_fps=$(number "$(status_value target_fps)" "$target_fps")
            if [ "$awake" = true ] && [ "$age" -ge 0 ] && [ "$age" -le "$health_max_age" ]; then healthy=true; fi
        fi
    fi
    printf '{"version":1,"at":%s,"enabled":%s,"awake":%s,"running":%s,"healthy":%s,"heartbeat_age":%s,"measured_fps":%s,"target_fps":%s,"frames":%s,"capture_ms":%s}\n' \
        "$(date +%s)" "$enabled" "$awake" "$running" "$healthy" "$age" "$fps" \
        "$target_fps" "$frames" "$capture_ms"
}

command=${1:-}
[ $# -gt 0 ] && shift
case "$command" in
    start) start_capture ;;
    stop) stop_capture ;;
    restart) stop_capture && start_capture ;;
    enable) touch "$dir/enabled" && start_capture ;;
    disable) rm -f "$dir/enabled" && stop_capture ;;
    status) show_status ;;
    health) health ;;
    report) report ;;
    log) tail -n "$(number "${1:-50}" 50)" "$log" ;;
    probe) current_jar && CLASSPATH="$jar" app_process / "$main" probe "$@" ;;
    version) current_jar && CLASSPATH="$jar" app_process / "$main" version ;;
    *) usage ;;
esac
