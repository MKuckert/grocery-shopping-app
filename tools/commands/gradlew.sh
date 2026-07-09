#!/usr/bin/env bash
# Description: Calls Gradle with a given command. Pass command to execute as an object with a string attribute `command`, e.g. `gradlew({command: "build"}`
# Param: command string required "gradlew command to call, e.g. `help`, `:app:assembleDebug`"

if [[ $# -lt 2 ]]; then
    echo 'Call with command to execute as a single string parameter `command`: gradlew({command: "build"}' >&2
    exit 1
fi

COMMAND=""
case "$1" in
    --command)
        COMMAND="$2"
        shift 2
        ;;
    *)
        echo "Unknown parameter $1 given" >&2
        exit 1
        ;;
esac

if [[ -z "$COMMAND" ]]; then
    echo "No command provided. Please provide a command to execute as a single string parameter 'command'" >&2
    exit 1
fi

if [ -f .env ]; then
    source .env
fi

./gradlew $COMMAND
