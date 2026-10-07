# erp-purchasing-agent

ReAct-style AI agent that consumes the tools exposed by [erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server) via the Model Context Protocol. Given a natural-language purchasing goal ("check what's below minimum stock and create purchase orders for the affected suppliers"), the agent iteratively reasons, calls MCP tools, observes the results, and decides the next step until the goal is met.

Implements **Human-in-the-Loop (HITL)**: the agent pauses before executing irreversible actions (e.g. sending a purchase order), persists its full conversation state in Redis, and resumes from exactly that point once the operator approves or rejects the action.

Project 2b of the [AI Engineer Roadmap · Java + Spring AI](https://github.com/Toleflaco/ai-engineer-roadmap-java), Session 16 (ReAct agents).

## Tech stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0 (`spring-ai-starter-mcp-client-webmvc`)
- Anthropic Claude Sonnet 4.5 (via `spring-ai-starter-model-anthropic`)
- Redis (conversation state persistence for HITL)
- Docker Compose

## Architecture

User goal (natural language)
|
v
+------------------+ MCP Streamable HTTP +--------------------+
| agent (this) | <----------------------------> | erp-mcp-server |
| ReAct loop | :8080/mcp | (separate repo) |
| Spring AI | | Spring Boot MCP |
| Claude Sonnet | | server + Postgres |
+------------------+ +--------------------+
|
| HITL pause (202 Accepted)
v
+------------------+
| Redis | ← full conversation state persisted
| (AgentRunSession)|
+------------------+
|
| operator approve/reject → resume from exact pause point
v
+------------------+
| agent resumes |
+------------------+


The agent runs the standard ReAct cycle: **Reason → Act (call MCP tool) → Observe → repeat** until the LLM decides the goal is satisfied. Tool discovery is dynamic: the agent asks the MCP server for the current tool catalog on startup instead of hardcoding tool names.

### Human-in-the-Loop flow

1. Agent receives a purchasing goal and starts the ReAct loop (`POST /agent/run` → `202 Accepted` + `runId`).
2. When the agent reaches an irreversible action, it pauses and persists the full conversation state (messages, tokens, cost, iteration count) in Redis under the `runId`.
3. The operator reviews the proposed action and calls `POST /agent/run/{runId}/approve` to approve and resume.
4. The agent resumes from the exact pause point — no context is lost, no LLM call is repeated.

> Note: a `reject` endpoint is on the roadmap but not yet implemented. Currently, cancelling a pending run means letting its Redis session expire.

## Quick start

Prerequisite: [erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server) running on `localhost:8080` (start it with `docker compose up --build` from that repo).

```bash
git clone git@github.com:Toleflaco/erp-purchasing-agent.git
cd erp-purchasing-agent
export ANTHROPIC_API_KEY=<your-key>
docker compose up --build
```

### Key endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/agent/run` | Start a new purchasing goal (returns `runId` if paused) |
| `POST` | `/agent/run/{runId}/approve` | Approve a paused run and resume execution |

### Example requests

#### Start a run that pauses for approval

**Request**

```bash
curl -X POST http://localhost:8082/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"check stock levels and place purchase orders"}'
```

**Response** — `202 Accepted`

```json
{
  "run_id": "run-abc",
  "original_prompt": "check stock levels and place purchase orders",
  "pending_tool_calls": [
    {
      "name": "sendPurchaseOrder",
      "arguments": "{\"purchaseOrderId\":42}"
    }
  ],
  "iterations": 1,
  "tokens_total": 160,
  "duration_ms": 1232,
  "cost_usd": 0.0023
}
```

#### Approve a paused run

**Request**

```bash
curl -X POST http://localhost:8082/agent/run/run-abc/approve
```

**Response** — `200 OK`

```json
{
  "text": "purchase order approved and processed",
  "iterations": 1,
  "tokens_total": 160,
  "duration_ms": 1232,
  "cost_usd": 0.0023
}
```

#### Approve a run that no longer exists

**Request**

```bash
curl -X POST http://localhost:8082/agent/run/run-missing/approve
```

**Response** — `410 Gone`

```json
{
  "type": "about:blank",
  "title": "Run session not found",
  "status": 410,
  "detail": "Run session not found: run-missing",
  "run_id": "run-missing"
}
```

## Related repositories

- **[erp-mcp-server](https://github.com/Toleflaco/erp-mcp-server)** — The MCP server this agent consumes.
- **[ai-engineer-roadmap-java](https://github.com/Toleflaco/ai-engineer-roadmap-java)** — Hub roadmap that includes this project as Session 16.
- **[document-analyzer-ai](https://github.com/Toleflaco/document-analyzer-ai)** — Phase 1 of the roadmap: Spring AI + Anthropic Claude for structured CV extraction.

---

*Last updated: 2026-10-06*
