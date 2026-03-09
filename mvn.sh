#!/bin/bash
# Maven wrapper script that moves all files into the workspace so the build works inside the codex sandbox.

scriptdir="$(dirname "$0")"

source /home/chabicht/.java17

mvn -B --settings "$scriptdir/.m2/settings.xml" -Dmaven.repo.local="$scriptdir/.m2/repository" "$@"
