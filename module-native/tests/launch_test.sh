#!/usr/bin/env bash

set -eu
HostPath=$PATH
. "$(dirname "$0")/../common.sh"
PATH=$HostPath
export PATH
Package=com.example.target
Checks=0

ExpectLaunch()
{
    Checks=$((Checks + 1))
    StartOutput=$2
    Status=0
    ReadLaunchEvidence || Status=$?
    if [ "$Status" -ne "$1" ]; then
        printf 'FAIL: launch evidence case %s\n' "$Checks" >&2
        exit 1
    fi
}

Valid=$(printf 'Status: ok\nLaunchState: COLD\nActivity: com.example.target/.MainActivity\nTotalTime: 200\nWaitTime: 220\nComplete\n')
ExpectLaunch 0 "$Valid"
ExpectLaunch 0 "$(printf '%s\n' "$Valid" | sed 's/COLD/WARM/')"
ExpectLaunch 0 "$(printf '%s\n' "$Valid" | sed 's/COLD/HOT/; s/TotalTime: 200/TotalTime: 0/')"
ExpectLaunch 0 "$(printf '%s\n' "$Valid" | sed 's/COLD/RELAUNCH/')"
ExpectLaunch 0 "$(printf '%s\n' "$Valid" | sed 's|/.MainActivity|/com.example.target.GameActivity|')"
ExpectLaunch 0 "$(printf '%s\n' "$Valid" | sed 's/TotalTime: 200/TotalTime: 13800/; s/WaitTime: 220/WaitTime: 13820/')"
ExpectLaunch 1 "$(printf 'Status: ok\nLaunchState: UNKNOWN (0)\nActivity: com.example.target/.MainActivity\nWaitTime: 13800\nComplete\n')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed '/TotalTime:/d')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's/TotalTime: 200/TotalTime: -1/')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's/TotalTime: 200/TotalTime: not-a-number/')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's/Status: ok/Status: timeout/')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's/com.example.target/com.example.other/')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed '/^Complete$/d')"
ExpectLaunch 1 "$(printf 'Status: ok\n%s\n' "$Valid")"
ExpectLaunch 1 "$(printf '%s\nunexpected trailing output\n' "$Valid")"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed '/^Activity:/d')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's/LaunchState: COLD/LaunchState: UNKNOWN (0)/')"
ExpectLaunch 1 "$(printf '%s\n' "$Valid" | sed 's|/.MainActivity|/../bad|')"
printf 'PASS: %s launch evidence cases.\n' "$Checks"
