# Freshdesk MCP Connector — Demo Talk Track

**Estimated time:** 3–5 minutes
**Audience:** Engineering Evaluators / System Architects

This demo shows the connector end-to-end: MCP initialization, tool discovery, Freshdesk retrieval, privacy controls, rate-limit handling, and bounded pagination.

The demo runs entirely against **synthetic Freshdesk data**. No real Freshdesk account, API key, customer data, or external credentials are required.

---

## 1. Before You Start

From the repository root:

```bash
docker compose up -d
bash demo/demo.sh
```

The demo runs a sequence of deterministic scenarios against the local mock environment.

### Architecture in one sentence

> “This is a read-only Freshdesk connector exposed through MCP: the MCP layer handles JSON-RPC and tool contracts, the service layer owns retrieval and policy logic, and the client layer handles Freshdesk HTTP communication, authentication, retries, and rate limits.”

---

# Demo Flow

| Beat | Scenario            | What it demonstrates          |
| ---- | ------------------- | ----------------------------- |
| 1    | MCP initialization  | Protocol compatibility        |
| 2    | Notifications       | MCP lifecycle completion      |
| 3    | Tool discovery      | Agent-readable tool contracts |
| 4    | Search              | Bounded ticket retrieval      |
| 5    | Ticket details      | Explicit ticket retrieval     |
| 6    | Conversations       | Privacy boundary              |
| 7    | Private-note policy | Fail-closed authorization     |
| 8    | Rate-limit retry    | Resilience                    |
| 9    | Pagination budget   | Bounded upstream usage        |

---

# Beat 1 — MCP Initialization

**Time:** ~20 seconds

### Action

Run the first scenario from:

```text
demo/demo.sh
```

### Show

```text
initialize
```

Highlight:

* MCP protocol version
* server capabilities
* successful initialization response

### Talk track

> “We start with the MCP lifecycle over HTTP. The client sends an `initialize` request and the connector responds with its capabilities and the protocol version it supports.”

> “For this assignment, I intentionally kept the transport simple and stateless using HTTP JSON-RPC rather than introducing additional session or streaming infrastructure.”

### Why it matters

This demonstrates that the connector is exposed as an MCP server rather than simply wrapping Freshdesk with another REST endpoint.

---

# Beat 2 — Initialization Notification

**Time:** ~10 seconds

### Show

```text
notifications/initialized
```

### Talk track

> “After initialization, the client sends the `notifications/initialized` notification. This completes the initialization lifecycle before the client starts using the available tools.”

### Key point

The connector is ready for MCP tool discovery and invocation.

---

# Beat 3 — Tool Discovery

**Time:** ~30 seconds

### Action

Show:

```text
tools/list
```

### Available tools

```text
search_tickets
list_tickets
get_ticket
get_ticket_conversations
```

### Talk track

> “The connector exposes four read-oriented tools. I deliberately kept the tool surface small and focused on the primitives an agent actually needs to retrieve Freshdesk information.”

> “The schemas describe the inputs and outputs explicitly, so an MCP client can discover how to use the tools without depending on implementation details.”

### Why it matters

The agent interacts with **well-defined tools**, rather than having direct unrestricted access to the Freshdesk API.

---

# Beat 4 — Ticket Search

**Time:** ~40 seconds

### Action

Invoke:

```text
search_tickets
```

using structured filters and keywords.

### Talk track

> “`search_tickets` provides the search primitive. The connector uses Freshdesk's supported search capabilities to narrow the candidate set and then applies the connector's supported keyword matching.”

> “I don't present this as unrestricted semantic search. Freshdesk's upstream search behavior has limitations, so the connector explicitly communicates its search scope rather than giving the agent a misleading impression of exhaustive search.”

### Bounded candidate scan

The connector caps the candidate set at:

```text
300 candidates
```

### Show, if present

```json
{
  "search_scope": {
    "candidate_limit": 300,
    "exhaustive": true
  }
}
```

### Talk track

> “The `search_scope` metadata tells the consuming agent whether the result represents the complete upstream result set or a bounded scan.”

### Why it matters

The agent gets **predictable retrieval behavior** instead of potentially triggering an unbounded search.

---

# Beat 5 — Ticket Details

**Time:** ~20 seconds

### Action

Invoke:

```text
get_ticket
```

### Talk track

> “For an individual ticket, we use `get_ticket`. This operation intentionally retrieves only the ticket itself and does not automatically pull conversation history.”

> “That keeps the normal ticket lookup small and avoids accidentally expanding the response with potentially sensitive conversation content.”

### Key design decision

```text
get_ticket
        │
        └── ticket data only

get_ticket_conversations
        │
        └── conversation data explicitly requested
```

---

# Beat 6 — Conversation Retrieval & Privacy

**Time:** ~30 seconds

### Action

Invoke:

```text
get_ticket_conversations
```

### Talk track

> “Conversation retrieval is a separate operation. Private notes are excluded by default, so the normal agent workflow receives customer-visible conversation content without automatically exposing internal notes.”

### Show, if present

```json
{
  "private_notes_excluded": 1
}
```

### Guardrail layers

The protection is intentionally implemented at multiple levels:

1. Private notes are not requested by default.
2. The service layer filters them before returning conversation results.
3. Explicit private-note access requires an operator policy gate.
4. The MCP tool cannot silently enable private-note access.

### Key message

> “The goal is to make private-note exposure an explicit deployment decision rather than an accidental side effect of a broad conversation endpoint.”

---

# Beat 7 — Private-Note Policy Gate

**Time:** ~25 seconds

### Action

Attempt:

```json
{
  "include_private_notes": true
}
```

while:

```text
FRESHDESK_ALLOW_PRIVATE_NOTES=false
```

### Expected result

```text
ACCESS_DENIED_BY_POLICY
```

### Talk track

> “There are two independent conditions for private-note access: the agent has to explicitly request it, and the deployment has to explicitly allow it.”

> “With the operator policy disabled, the service fails closed even when the tool argument requests private notes.”

### Why it matters

This prevents an MCP client or LLM from enabling sensitive-data access simply by changing a tool parameter.

---

# Beat 8 — Rate-Limit Handling

**Time:** ~35 seconds

### Action

Run the rate-limit scenario.

The mock server injects:

```text
HTTP 429 Too Many Requests
Retry-After: 2
```

### Expected behavior

The connector:

1. receives the `429`
2. reads `Retry-After`
3. waits for the requested delay
4. retries the upstream request
5. returns the successful result

### Talk track

> “Here we simulate Freshdesk rate limiting. The client layer recognizes the `429`, honors the `Retry-After` value, and retries the request instead of immediately returning a failure to the agent.”

> “The retry is handled below the agent layer. The LLM doesn't need to understand Freshdesk-specific retry semantics.”

### Show, if present

```json
{
  "meta": {
    "retries": 1,
    "upstream_calls": 2
  }
}
```

### Why it matters

An agent can generate repeated or concurrent tool calls. Rate-limit handling therefore belongs in the connector rather than relying on the LLM to manage upstream API behavior.

---

# Beat 9 — Deterministic Pagination & Scan Budget

**Time:** ~40 seconds

### Action

Invoke:

```text
list_tickets
```

with:

```text
status = resolved
```

### Talk track

> “For listing tickets, the connector traverses Freshdesk's ordered ticket pages and applies the requested status filtering within the connector.”

> “The important part is that an agent request can never trigger an unbounded scan. Each call has a strict traversal budget of five upstream pages, or up to 500 tickets.”

### Limits

```text
Maximum upstream pages:    5
Maximum scanned tickets:   500
Cursor:                    opaque
```

### Talk track

> “The cursor is opaque to the MCP caller but contains the information required to continue from the correct upstream position.”

> “This gives us deterministic continuation rather than restarting the scan from page one.”

### Why it matters

Without a scan budget, a sparse filter such as:

```text
status = resolved
```

could cause the connector to traverse a large portion of the Freshdesk dataset before finding enough matches.

The bounded approach deliberately trades potentially complete discovery for:

* predictable API usage
* bounded latency
* protection against accidental large scans

---

# Final 20-Second Summary

After Beat 9:

> “So the connector demonstrates the complete path from an MCP client to Freshdesk: protocol initialization, tool discovery, bounded search and listing, ticket and conversation retrieval, private-note protection, rate-limit retries, and deterministic pagination.”

> “The implementation is deliberately read-only and uses synthetic data for evaluation. For production, I would add MCP-layer authentication and authorization, centralized secrets management, stronger observability, distributed rate-limit coordination, and potentially a dedicated search layer for larger Freshdesk installations.”

---

# What This Demo Demonstrates

| Assignment requirement         | Demonstrated by                                |
| ------------------------------ | ---------------------------------------------- |
| Private Freshdesk connector    | MCP endpoint + Freshdesk client                |
| Agent-readable tickets         | `get_ticket`, `list_tickets`, `search_tickets` |
| Conversation retrieval         | `get_ticket_conversations`                     |
| Authentication                 | Freshdesk API key via Basic Auth               |
| MCP tool specification         | `tools/list` + tool schemas                    |
| Search primitive               | Beat 4                                         |
| List primitive                 | Beat 9                                         |
| Get primitive                  | Beat 5                                         |
| Rate-limit handling            | Beat 8                                         |
| Pagination                     | Beat 9                                         |
| Sensitive-data protection      | Beats 6 & 7                                    |
| Evaluation without credentials | Mock mode                                      |
| Failure testing                | Fault injection                                |
| Production considerations      | Final summary + README                         |

---

# If the Evaluator Has Only 2 Minutes

Prioritize these scenarios:

### 1. MCP initialization

Prove that the connector is actually exposed through MCP.

### 2. Tool discovery

Show:

```text
search_tickets
list_tickets
get_ticket
get_ticket_conversations
```

### 3. Search

Demonstrate an agent-oriented retrieval operation.

### 4. Private-note protection

Show the policy gate rejecting:

```text
include_private_notes=true
```

when the deployment policy is disabled.

### 5. 429 retry

Show:

```text
429 → Retry-After → retry → success
```

### 6. Pagination budget

Show:

```text
5 pages / 500 tickets
```

and explain why the limit exists.

---

