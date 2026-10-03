# Freshdesk MCP Connector — Demo Talk Track

**Estimated time:** 3–5 minutes
**Audience:** Engineering Evaluators / System Architects

This demo walks through the connector's MCP lifecycle, Freshdesk retrieval behavior, privacy controls, resilience, and bounded pagination.

The demo runs entirely against synthetic Freshdesk data, so **no real Freshdesk account, API key, customer data, or external credentials are required**.

---

## Before You Start

From the repository root:

```bash
docker compose up -d
bash demo/demo.sh
```

The scripted demo executes nine beats in sequence.

If you want to explain the architecture before running the script:

> “This connector exposes a small read-only Freshdesk interface as MCP tools. The MCP layer handles JSON-RPC requests, the service layer owns retrieval and policy logic, and the client layer handles Freshdesk HTTP communication, authentication, retries, and rate limits.”

---

## Beat 1 & 2 — MCP Handshake and Notifications

**Time:** ~30 seconds

### Action

Run Beats 1 and 2 from:

```text
demo/demo.sh
```

### What to show

* `initialize`
* negotiated MCP protocol version
* `notifications/initialized`
* HTTP response behavior

### Talk track

> “We start with the MCP lifecycle over HTTP. This implementation targets the `2024-11-05` MCP handshake revision. The client sends `initialize`, the server returns its capabilities and negotiated protocol version, and the client then sends `notifications/initialized`.”

> “The connector intentionally uses a simple stateless HTTP JSON-RPC endpoint for this assignment rather than introducing SSE or a more complex session transport.”

### Why it matters

This demonstrates that the connector is not just a REST wrapper. It exposes the Freshdesk capabilities through an MCP-compatible tool interface that an MCP host can discover and invoke.

---

## Beat 3 & 4 — Tool Discovery and Bounded Search

**Time:** ~45 seconds

### Action

Run Beats 3 and 4.

### What to show

Focus on:

```text
tools/list
search_tickets
list_tickets
get_ticket
get_ticket_conversations
```

Then show a `search_tickets` invocation with structured filters and keywords.

### Talk track

> “The connector exposes four read-oriented tools. The important design choice is that search and listing are separate operations because they map to different Freshdesk retrieval semantics.”

> “`search_tickets` uses Freshdesk's search capability to narrow the candidate set using supported structured filters. Because the upstream search behavior does not provide unrestricted full-text search across every ticket field, keyword matching is deliberately performed over the fields supported by this connector rather than pretending that the search is globally exhaustive.”

> “The response also exposes search-scope metadata so an agent can distinguish between an exhaustive result set and a bounded candidate scan.”

### Important limitation

The connector does **not** claim to provide unrestricted semantic search over:

* ticket descriptions
* conversation bodies
* arbitrary Freshdesk fields

Instead, it makes the supported search scope explicit.

### What to point out

If present in the response, highlight:

```json
"search_scope": {
  "candidate_limit": 300,
  "exhaustive": true
}
```

The exact value of `exhaustive` depends on whether the bounded candidate set represents the complete upstream result set.

---

## Beat 5 & 6 — Ticket Details and Conversation Privacy

**Time:** ~45 seconds

### Action

Run Beats 5 and 6.

### What to show

First call:

```text
get_ticket
```

Then separately call:

```text
get_ticket_conversations
```

### Talk track

> “Ticket details and conversations are intentionally separated. `get_ticket` does not request conversations as part of the ticket response, which keeps the basic ticket payload small and avoids accidentally pulling internal conversation data into a normal ticket lookup.”

> “Conversation retrieval is explicit through `get_ticket_conversations`. Private notes are excluded by default, so the normal agent workflow only receives customer-visible conversation content.”

### What to point out

Show the response metadata indicating that private notes were excluded, for example:

```json
{
  "private_notes_excluded": 1
}
```

### Why it matters

This is a defense-in-depth design:

1. Private notes are not requested by default.
2. The service layer filters them before returning conversation results.
3. Explicit private-note access requires an operator policy gate.
4. The MCP tool itself does not silently expose private notes.

> “The goal is to make private-note exposure an explicit deployment decision rather than an accidental side effect of a broad conversation endpoint.”

---

## Beat 7 — Operator Security Gate

**Time:** ~30 seconds

### Action

Run Beat 7.

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

### Expected behavior

The request fails with:

```text
ACCESS_DENIED_BY_POLICY
```

### Talk track

> “There are two independent conditions for private-note access: the agent must explicitly request it, and the deployment must explicitly allow it.”

> “With the operator flag disabled, the service fails closed even when the tool argument requests private notes.”

### Why it matters

This prevents an MCP client or agent from enabling sensitive data exposure solely through tool arguments.

---

## Beat 8 — Rate-Limit Handling and Retry

**Time:** ~45 seconds

### Action

Run Beat 8.

The mock server injects:

```text
HTTP 429 Too Many Requests
Retry-After: 2
```

### What to show

The connector should:

1. receive the `429`
2. read `Retry-After`
3. wait for the requested delay
4. retry the upstream request
5. return the successful result

### Talk track

> “Here we simulate a Freshdesk rate-limit response. The client layer recognizes the `429`, honors the `Retry-After` value, and retries the request rather than immediately surfacing a failure to the agent.”

> “The response metadata makes the retry visible, so the evaluator can verify that the connector actually made two upstream attempts.”

### What to point out

For example:

```json
"meta": {
  "retries": 1,
  "upstream_calls": 2
}
```

### Why it matters

Rate limiting is particularly important for an agent-facing connector because one agent request can trigger multiple upstream API calls. Retry behavior therefore belongs in the connector/client layer rather than being left to the LLM.

---

## Beat 9 — Deterministic Pagination and Scan Budget

**Time:** ~45 seconds

### Action

Run Beat 9.

Use a `list_tickets` request with a filter such as:

```text
status = resolved
```

### Talk track

> “Freshdesk's ticket listing endpoint does not provide the status filter we need as a simple upstream query parameter, so the connector performs status filtering client-side while traversing the ordered ticket pages.”

> “The important part is that we do not allow an agent request to trigger an unbounded scan. Each call has a strict upstream traversal budget of five pages, or up to 500 tickets.”

> “The cursor is opaque to the MCP caller but contains the information required to continue from the correct upstream position. This gives us deterministic continuation rather than restarting the scan from page one.”

### What to point out

Highlight:

```text
maximum upstream pages per call: 5
maximum scanned tickets per call: 500
cursor: opaque
```

### Why it matters

Without a scan budget, a sparse filter such as `status=resolved` could cause the connector to traverse a large portion of the Freshdesk dataset before finding enough matches.

The bounded approach trades complete discovery for predictable API usage and latency.

---

# Final 20-Second Summary

After Beat 9, close with:

> “So the connector demonstrates the complete path from an MCP client to Freshdesk: protocol handshake, tool discovery, bounded search and listing, ticket and conversation retrieval, private-note protection, rate-limit retries, and deterministic pagination.”

> “The implementation is deliberately read-only and uses synthetic data for evaluation. The main production-hardening steps would be MCP-layer authentication and authorization, centralized secrets management, stronger observability, distributed rate-limit coordination, and potentially a more capable search/indexing layer for large Freshdesk installations.”

---

# What This Demo Demonstrates

| Assignment requirement                 | Demonstrated by                                |
| -------------------------------------- | ---------------------------------------------- |
| Private Freshdesk connector            | Complete MCP endpoint + Freshdesk client       |
| Agent-readable tickets                 | `get_ticket`, `list_tickets`, `search_tickets` |
| Conversation retrieval                 | `get_ticket_conversations`                     |
| Authentication                         | Freshdesk API key via Basic Auth               |
| MCP tool specification                 | `tools/list` + tool schemas                    |
| Search primitive                       | Beat 4                                         |
| List primitive                         | Beat 9                                         |
| Get primitive                          | Beat 5                                         |
| Rate-limit handling                    | Beat 8                                         |
| Pagination                             | Beat 9                                         |
| Sensitive-data protection              | Beats 6 & 7                                    |
| Working evaluation without credentials | Mock mode                                      |
| Failure testing                        | Fault injection                                |
| Production considerations              | Final summary + README                         |

---

# Suggested Demo Flow

If time is limited, prioritize the following:

1. **Handshake** — prove MCP integration.
2. **Tool discovery** — show the available primitives.
3. **Search** — demonstrate agent-oriented retrieval.
4. **Private-note protection** — demonstrate the security boundary.
5. **429 retry** — demonstrate resilience.
6. **Pagination budget** — demonstrate control over upstream API usage.

The evaluator should be able to understand the connector's architecture and major trade-offs without reading the implementation first.
