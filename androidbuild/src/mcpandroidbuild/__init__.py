from .server import create_app
import uvicorn


def main():
    """Android Project MCP Server - Building Android project"""
    app = create_app()
    uvicorn.run(app, host="0.0.0.0", port=8000)


if __name__ == "__main__":
    main()
