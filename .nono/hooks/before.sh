#!/usr/bin/env bash
backgrounded.sh start grocery-shopping-app-mcp-androidbuild mcp-commands --dir src --scripts tools/commands --port 12001
backgrounded.sh start grocery-shopping-app-mcp-websearch docker mcp gateway run --profile web --transport streaming --port 12002
