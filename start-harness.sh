#!/usr/bin/env bash

.nono/hooks/before.sh
nono wrap --profile .nono/profile.json -v -- opencode "$@"
.nono/hooks/after.sh
