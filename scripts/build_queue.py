#!/usr/bin/env python3
"""Run heavy jobs through this PC's build queue when it has one, and directly otherwise.

Two environment variables, both optional and read from the User scope too, since a git hook
or a shell started before they were set misses them:

HUSHMESSENGER_BUILD_WRAPPER names a PowerShell script that runs Gradle on this machine. It is
called as ``<wrapper> -ProjectDir <repo> -Tasks <task>...`` and must pass the exit code through.

BUILD_QUEUE_SCRIPT names the machine queue. Desktop patch runs and the stock compatibility
report go through ``<queue> -Label <label> -Run <command line>``, so they wait for a slot
instead of piling onto other builds. BUILD_QUEUE_PRIORITY=release, set by the caller, puts a
release ahead of everyday builds.

Unset, Gradle runs through gradlew and every other job starts straight away, as before.
"""

import os
import shutil
import sys
from pathlib import Path

WRAPPER = "HUSHMESSENGER_BUILD_WRAPPER"
QUEUE = "BUILD_QUEUE_SCRIPT"

# How long a queued job may wait for a slot on top of its own time limit. Another session's
# release gate can hold both slots for most of an hour.
QUEUE_WAIT_SECONDS = 3 * 60 * 60


def setting(name):
    """The variable from this process, or from the User scope on Windows.

    Set to an empty string in the process, it stays off whatever the User scope says.
    """
    if name in os.environ or sys.platform != "win32":
        return os.environ.get(name, "").strip()
    return _user_setting(name)


def _user_setting(name):
    import winreg

    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as key:
            return str(winreg.QueryValueEx(key, name)[0]).strip()
    except OSError:
        return ""


def quote(text):
    """A PowerShell single-quoted literal, which expands nothing."""
    return "'" + str(text).replace("'", "''") + "'"


def shell():
    return shutil.which("pwsh") or shutil.which("powershell") or "pwsh"


def existing(name):
    path = setting(name)
    if path and not Path(path).is_file():
        raise ValueError(f"{name} names {path}, which is not there")
    return path


def gradle(root, tasks):
    """The command that runs Gradle tasks in root, through the wrapper when one is set."""
    wrapper = existing(WRAPPER)
    if not wrapper:
        return [str(Path(root) / ("gradlew.bat" if sys.platform == "win32" else "gradlew")), *tasks]
    script = "& {} -ProjectDir {} -Tasks @({}); exit $LASTEXITCODE".format(
        quote(wrapper), quote(root), ", ".join(quote(task) for task in tasks)
    )
    return [shell(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script]


def queued(command, label):
    """The command run inside a queue slot when the machine has a queue."""
    queue = existing(QUEUE)
    if not queue:
        return list(command)
    if any(part == "" for part in command):
        raise ValueError("an empty argument would be dropped on its way through the queue")
    run ="& {}; exit $LASTEXITCODE".format(" ".join(quote(part) for part in command))
    return [
        shell(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-File", queue, "-Label", label, "-Run", run,
    ]


def time_limit(seconds, gradle_job=False):
    """A job's own limit plus the longest wait for a slot, when it goes through a queue."""
    waits = existing(WRAPPER) if gradle_job else existing(QUEUE)
    return seconds + QUEUE_WAIT_SECONDS if waits else seconds
