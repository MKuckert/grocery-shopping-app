import os
import subprocess
from pathlib import Path

from mcp.server.lowlevel import Server
from mcp.server.sse import SseServerTransport
from mcp.shared.exceptions import McpError
from mcp.types import (
    INVALID_PARAMS,
    Annotated,
    ErrorData,
    Field,
    TextContent,
    Tool,
)
from pydantic import BaseModel
from starlette.applications import Starlette
from starlette.routing import Mount, Route


class GradleTask(BaseModel):
    """Parameters"""

    task: Annotated[
        str,
        Field(
            description="The gradle task to execute, e.g., ':app:assembleDebug', ':help', etc. . Important: This is not a shell command: No pipes, no redirections, no multiple commands. Just the gradle task name.",
        ),
    ]


server = Server("build")


@server.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="gradlew",
            description="Execute gradlew to build the Android project. You have to pass the task as an argument. Returns complete raw output of the command.",
            inputSchema=GradleTask.model_json_schema(),
        ),
    ]


@server.call_tool()
async def call_tool(name, arguments: dict) -> list[TextContent]:
    try:
        args = GradleTask(**arguments)
    except ValueError as e:
        raise McpError(ErrorData(code=INVALID_PARAMS, message=str(e)))
    script_dir = Path(__file__).resolve().parent
    project_dir = script_dir.parent.parent.parent.parent / "src"

    print(f"Executing `{name} {args.task}` in project directory: {project_dir}")

    command = [""]
    if name == "gradlew":
        command = [str(script_dir / "gradlew.sh"), args.task]

    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
        cwd=project_dir,
    )
    all_lines = result.stdout.decode("utf-8").splitlines()
    response = "\n".join(all_lines)
    return [TextContent(type="text", text=f"{response}")]


def create_app() -> Starlette:
    sse = SseServerTransport("/messages/")

    async def handle_sse(request):
        async with sse.connect_sse(
            request.scope, request.receive, request._send
        ) as streams:
            await server.run(
                streams[0],
                streams[1],
                server.create_initialization_options(),
                raise_exceptions=True,
            )

    return Starlette(
        routes=[
            Route("/sse", endpoint=handle_sse),
            Mount("/messages/", app=sse.handle_post_message),
        ]
    )
