"""Start every MCP server declared in mcp.json and speak the stdio handshake to it.

This proves a server starts from the committed lockfiles and answers initialize
and tools/list. It calls no tool: chrome needs a running Chrome and zotero needs
the Zotero desktop app, neither of which pnpm or uv can install.

Run it from the repo root:

    uv run --project python --locked python scripts/mcp_probe.py
"""

import asyncio
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import TextIO

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parent.parent
TIMEOUT_SECONDS = float(os.environ.get("MCP_PROBE_TIMEOUT_MS", "120000")) / 1000


def expand(value: str) -> str:
    """Resolve ${env:NAME} the way scripts/agents.mjs does before a sync."""

    def resolve(match: re.Match[str]) -> str:
        name = match.group(1)
        resolved = os.environ.get(name) or (
            str(ROOT) if name == "AGENTS_HOME" else None
        )
        if resolved is None:
            raise SystemExit(f"mcp.json uses ${{env:{name}}}, which is not set")
        return resolved

    return re.sub(r"\$\{env:([A-Z0-9_]+)\}", resolve, value)


async def handshake(params: StdioServerParameters, errlog: TextIO) -> str:
    async with stdio_client(params, errlog=errlog) as (read, write):
        async with ClientSession(read, write) as session:
            result = await session.initialize()
            tools = (await session.list_tools()).tools
    if not tools:
        raise RuntimeError("tools/list returned no tools")
    return f"{len(tools)} tools, {result.server_info.name} {result.server_info.version}"


def describe(error: BaseException) -> str:
    """Unwrap the task-group wrapper anyio and the SDK add around a real failure."""
    while isinstance(error, BaseExceptionGroup) and error.exceptions:
        error = error.exceptions[0]
    return f"{type(error).__name__}: {error}"


async def probe(name: str, spec: dict) -> str:
    params = StdioServerParameters(
        command=expand(spec["command"]),
        args=[expand(argument) for argument in spec.get("args", [])],
        env={key: expand(value) for key, value in spec.get("env", {}).items()},
        cwd=ROOT,
    )
    # Server logging belongs in the failure report, not in the CI log of a pass.
    with tempfile.TemporaryFile(
        mode="w+", encoding="utf-8", errors="replace"
    ) as errlog:
        try:
            summary = await handshake(params, errlog)
        except Exception as error:
            errlog.seek(0)
            tail = errlog.read().strip().splitlines()[-6:]
            detail = "".join(f"{os.linesep}     {line}" for line in tail)
            raise RuntimeError(f"{describe(error)}{detail}") from None
    return f"{name}: {summary}"


async def main() -> int:
    servers = (
        json.loads((ROOT / "mcp.json").read_text(encoding="utf-8")).get("mcpServers")
        or {}
    )
    if not servers:
        print("mcp.json declares no servers", file=sys.stderr)
        return 1

    failed = 0
    for name, spec in servers.items():
        try:
            summary = await asyncio.wait_for(probe(name, spec), TIMEOUT_SECONDS)
        except Exception as error:  # one broken server must not hide the others
            failed += 1
            print(f"FAIL {name}: {error}", file=sys.stderr)
        else:
            print(f"ok   {summary}")

    if failed:
        print(
            f"{failed} of {len(servers)} MCP server(s) failed the handshake",
            file=sys.stderr,
        )
        return 1
    print(f"{len(servers)} MCP server(s) started and answered initialize + tools/list")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
