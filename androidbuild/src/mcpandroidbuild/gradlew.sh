#!/usr/bin/env bash

if [ -f .env ]; then
    source .env
    echo "Loading environment variables from .env file"
fi

./gradlew $1
