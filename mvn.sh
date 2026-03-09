#!/bin/bash

scriptdir="$(dirname "$0")"

source /home/chabicht/.java17

mvn -B --settings "$scriptdir/.m2/settings.xml" -Dmaven.repo.local="$scriptdir/.m2/repository" "$@"
