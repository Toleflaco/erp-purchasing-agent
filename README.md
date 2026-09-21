# erp-purchasing-agent

ReAct-style AI agent that consumes the tools exposed by [erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server) via the Model Context Protocol. Given a natural-language purchasing goal ("check what's below minimum stock and create purchase orders for the affected suppliers"), the agent iteratively reasons, calls MCP tools, observes the results, and decides the next step until the goal is met.

Project 2b of the [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java), Session 16 (ReAct agents).

## Tech stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0 (`spring-ai-starter-mcp-client-webmvc`)
- Anthropic Claude Sonnet 4.5 (via `spring-ai-starter-model-anthropic`)
- Docker Compose

## Architecture

```
User goal (natural language)
        |
        v
+------------------+       MCP Streamable HTTP       +--------------------+
|   agent (this)   | <----------------------------> |  erp-mcp-server     |
|  ReAct loop      |       :8080/mcp                 |  (separate repo)    |
|  Spring AI       |                                  |  Spring Boot MCP    |
|  Claude Sonnet   |                                  |  server + Postgres  |
+------------------+                                  +--------------------+
```

The agent runs the standard ReAct cycle: **Reason → Act (call MCP tool) → Observe → repeat** until the LLM decides the goal is satisfied. Tool discovery is dynamic: the agent asks the MCP server for the current tool catalog on startup instead of hardcoding tool names.

## Quick start

Prerequisite: [erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server) running on `localhost:8080` (start it with `docker compose up --build` from that repo).

```bash
git clone git@github.com:Toleflaco/erp-purchasing-agent.git
cd erp-purchasing-agent
export ANTHROPIC_API_KEY=<your-key>
./mvnw spring-boot:run
```

The agent exposes a REST endpoint to submit purchasing goals in natural language. See the source for the exact endpoint path and payload shape.

## Related repositories

- **[erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server)** — The MCP server this agent consumes.
- **[ai-engineer-roadmap-java](https://github.com/Toleflaco/ai-engineer-roadmap-java)** — Hub roadmap that includes this project as Session 16.
- **[document-analyzer-ai](https://github.com/Toleflaco/document-analyzer-ai)** — Phase 1 of the roadmap: Spring AI + Anthropic Claude for structured CV extraction.

---

*Last updated: 2026-09-21*
