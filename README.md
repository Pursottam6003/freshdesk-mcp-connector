# Freshdesk MCP Connector

A focused, read-only **Model Context Protocol (MCP) connector for Freshdesk**, built with Java 17 and Spring Boot 3.3.5.

The connector exposes Freshdesk ticket operations as MCP tools over stateless JSON-RPC 2.0 HTTP, allowing an AI agent to search, list, inspect, and retrieve ticket conversations while enforcing bounded retrieval, rate-limit handling, and private-note controls.

---

## Evaluator Quickstart

**No Freshdesk account or API credentials are required.**

The repository includes an in-process Freshdesk mock with 60 synthetic support tickets and configurable fault injection.

Run:

```bash
docker compose up -d
bash demo/demo.sh
```

That's it.

The service starts on:

```text
http://localhost:8080
```

The default runtime profile is `mock`.

The scripted demo walks through:

1. MCP initialization
2. MCP notification handling
3. Tool discovery
4. Ticket search
5. Ticket retrieval
6. Conversation retrieval and private-note filtering
7. Private-note security policy
8. `429` rate-limit retry
9. Bounded pagination and cursor continuation

For the detailed presentation talk track, see:

```text
demo/DEMO.md
```

---

## Assignment Coverage

This implementation is designed around the Freshdesk private-connector requirements:

| Requirement                  | Implementation                                          |
| ---------------------------- | ------------------------------------------------------- |
| Private Freshdesk connector  | Read-only Freshdesk REST API client exposed through MCP |
| Agent-readable tickets       | `get_ticket`, `list_tickets`, `search_tickets`          |
| Conversation access          | `get_ticket_conversations`                              |
| Authentication               | Freshdesk API key using HTTP Basic Auth                 |
| List primitive               | `list_tickets`                                          |
| Get primitive                | `get_ticket`                                            |
| Search primitive             | `search_tickets`                                        |
| MCP interface                | JSON-RPC 2.0 `/mcp` endpoint                            |
| Tool schemas                 | `tools/list` exposes JSON schemas                       |
| Rate-limit handling          | `429` + `Retry-After` support                           |
| Pagination                   | Bounded upstream traversal + opaque cursors             |
| Sensitive-data protection    | Private notes excluded by default                       |
| No real credentials required | Synthetic mock environment                              |
| Failure testing              | Configurable mock fault injection                       |
| Automated verification       | JUnit 5 + Mockito + WireMock integration tests          |

---

# 1. Architecture

The connector bridges an AI agent / MCP host and Freshdesk:

```text
┌─────────────────────────┐
│      AI Agent / Host    │
│  Claude / MCP Client    │
└────────────┬────────────┘
             │
             │ HTTP POST /mcp
             │ JSON-RPC 2.0
             ▼
┌────────────────────────────────────────────────────────┐
│                 Freshdesk MCP Connector                 │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Tool Layer                                       │  │
│  │ McpController + Tool Schema Registry             │  │
│  └──────────────────────────┬───────────────────────┘  │
│                             │                          │
│  ┌──────────────────────────▼───────────────────────┐  │
│  │ Service Layer                                    │  │
│  │ TicketService + Cursor Engine + Policy Rules    │  │
│  └──────────────────────────┬───────────────────────┘  │
│                             │                          │
│  ┌──────────────────────────▼───────────────────────┐  │
│  │ Client Layer                                     │  │
│  │ FreshdeskHttp + RetryExecutor                    │  │
│  └──────────────────────────┬───────────────────────┘  │
└─────────────────────────────┼──────────────────────────┘
                              │
                              │ Basic Auth
                              │ apikey:X
                              ▼
             ┌──────────────────────────────────┐
             │ Freshdesk REST API v2 / Mock     │
             └──────────────────────────────────┘
```

### Tool Layer

Responsible for:

* JSON-RPC request handling
* MCP method dispatch
* Input validation
* Tool schema definitions
* Structured MCP responses

### Service Layer

Responsible for:

* Ticket retrieval strategies
* Search behavior
* Client-side filtering
* Opaque cursor encoding/decoding
* Pagination budgets
* Private-note policy enforcement

### Client Layer

Responsible for:

* Freshdesk HTTP communication
* API-key authentication
* Retry behavior
* `429` handling
* Upstream error translation

This separation keeps Freshdesk-specific HTTP concerns out of the MCP tool layer.

---

# 2. Freshdesk API Constraints and Design Decisions

The connector is designed around several important Freshdesk API behaviors.

| Constraint     | Upstream behavior                                                                                     | Connector design                                                                 |
| -------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Authentication | API key can be supplied using HTTP Basic Auth                                                         | Centralized in `FreshdeskHttp`                                                   |
| Search         | Freshdesk search uses structured query syntax and has bounded pagination                              | Upstream filtering + bounded candidate retrieval                                 |
| Search scope   | Search results are bounded by Freshdesk pagination/search limits                                      | `search_scope` metadata makes the scope explicit                                 |
| Ticket listing | `/tickets` does not provide the status filtering required by the connector as a simple list parameter | Client-side status filtering over ordered pages                                  |
| Conversations  | Ticket conversations can contain private/internal notes                                               | Conversations are retrieved separately and private notes are excluded by default |
| Rate limiting  | Upstream can return `429` with retry information                                                      | Connector honors `Retry-After` and retries within configured limits              |

The design intentionally exposes these constraints instead of hiding them from the agent.

---

# 3. MCP Interface

## Endpoint

```text
POST /mcp
```

## Supported Protocol Version

```text
2024-11-05
```

The implementation targets this MCP protocol revision for the assignment.

## Supported Methods

### `initialize`

Negotiates:

* protocol version
* server capabilities
* server identity

### `notifications/initialized`

Acknowledges client initialization.

The implementation responds with:

```text
HTTP 202 Accepted
```

### `tools/list`

Returns the available MCP tools and their JSON schemas.

### `tools/call`

Executes a tool by name with validated arguments.

---

# 4. Available Tools

The connector exposes four read-oriented tools.

| Tool                       | Purpose                                                                       |
| -------------------------- | ----------------------------------------------------------------------------- |
| `search_tickets`           | Search for tickets using structured filters and keyword matching              |
| `list_tickets`             | Deterministically traverse tickets with optional client-side status filtering |
| `get_ticket`               | Retrieve a single ticket                                                      |
| `get_ticket_conversations` | Retrieve conversations while protecting private notes                         |

---

## `search_tickets`

Used when the agent has a specific ticket topic, tag, status, priority, or type to investigate.

### Arguments

```text
query              string   optional
status             string   optional
priority           string   optional
type               string   optional
limit              integer  default 10, max 30
```

Supported status values:

```text
open
pending
resolved
closed
```

Supported priority values:

```text
low
medium
high
urgent
```

### Search behavior

The connector combines:

1. Freshdesk server-side query filters
2. Candidate retrieval
3. Local keyword evaluation

Keyword matching is performed against the fields supported by the connector, primarily ticket subject and tags.

The connector does **not** claim to provide unrestricted full-text search across descriptions or conversation bodies.

### Search scope

Responses include metadata describing the search scope, allowing the agent to distinguish a complete upstream result set from a bounded candidate scan.

Example:

```json
{
  "search_scope": {
    "candidate_limit": 300,
    "exhaustive": true
  }
}
```

---

# 5. `list_tickets` and Bounded Pagination

`list_tickets` is designed for chronological exploration rather than targeted search.

### Arguments

```text
status     string   optional
cursor     string   optional
limit      integer  default 10, max 30
```

Example use case:

```text
"Show me recent resolved tickets."
```

The connector:

1. retrieves Freshdesk ticket pages in deterministic order
2. applies the optional status filter locally
3. returns matching tickets
4. produces an opaque cursor when more data can be retrieved

---

## Why Client-Side Status Filtering?

The connector needs to support queries such as:

```text
status = resolved
```

while preserving deterministic pagination.

Instead of pretending that the upstream `/tickets` endpoint provides the required status filter, the connector traverses ordered pages and applies the filter itself.

This creates a bounded scanning problem.

---

# 6. Five-Page / 500-Ticket Scan Budget

A sparse status filter could otherwise cause an agent request to scan a very large number of upstream tickets.

The connector therefore imposes a hard budget:

```text
Maximum upstream pages per invocation: 5
Maximum tickets scanned per invocation: 500
```

With the Freshdesk page size used by the connector:

```text
5 pages × 100 tickets = 500 tickets
```

If the requested result size is not satisfied within the budget, the connector stops and returns a continuation cursor.

Example cursor state:

```json
{
  "page": 3,
  "offset": 42,
  "status": "open",
  "fingerprint": "a3f8c..."
}
```

The cursor is opaque to the MCP caller.

### Why this matters

The budget prevents:

* runaway upstream API calls
* excessive latency
* uncontrolled API consumption
* large hidden scans triggered by an agent

The trade-off is explicit: a sparse query may require multiple tool calls to exhaustively traverse the dataset.

---

# 7. Search vs. Listing

The connector intentionally provides two retrieval paths.

```text
                     Agent Objective
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       "Find tickets about X"    "Get recent tickets"
              │                         │
              ▼                         ▼
      search_tickets              list_tickets
              │                         │
              │                         ├── Ordered traversal
              │                         ├── Cursor continuation
              │                         └── 500-ticket budget
              │
              ├── Structured filters
              ├── Candidate limit
              └── Search-scope metadata
```

### `search_tickets`

Best suited for:

* known keywords
* tags
* ticket types
* priority
* status
* targeted discovery

### `list_tickets`

Best suited for:

* chronological browsing
* recent ticket inspection
* deterministic continuation
* workflows where the agent needs to progressively inspect tickets

Keeping these operations separate makes their limitations explicit to the agent.

---

# 8. Private Notes Protection

Freshdesk conversations may contain internal agent notes and other information that should not normally enter an agent context.

The connector therefore uses defense-in-depth controls.

```text
Freshdesk Conversation
          │
          ▼
   Raw Conversation Item
          │
          ▼
   Is private == true?
       │          │
      No         Yes
       │          │
       │          ▼
       │    Operator policy
       │          │
       │     ┌────┴────┐
       │     │         │
       │    Deny      Allow
       │     │         │
       ▼     ▼         ▼
     Include Exclude  Include
              │
              ▼
       Response Payload
```

### Protection rules

#### 1. Ticket isolation

`get_ticket` never requests:

```text
include=conversations
```

This prevents a normal ticket lookup from automatically retrieving conversation data.

#### 2. Explicit conversation retrieval

Conversation data is retrieved only through:

```text
get_ticket_conversations
```

#### 3. Private notes excluded by default

The default is:

```text
include_private_notes = false
```

#### 4. Operator security gate

Private-note exposure additionally requires:

```text
FRESHDESK_ALLOW_PRIVATE_NOTES=true
```

If an agent requests:

```json
{
  "include_private_notes": true
}
```

while the environment policy is disabled, the connector fails closed with:

```text
ACCESS_DENIED_BY_POLICY
```

#### 5. Filtering before response mapping

Private conversation items are filtered before they are included in the tool response.

The response can also expose counts such as:

```text
private_notes_excluded
private_notes_included
```

without exposing the private content itself.

---

# 9. HTTP Client and Resilience

Freshdesk communication is encapsulated behind the client layer.

The implementation uses Spring's:

```text
RestClient
```

with a dedicated retry executor.

## `429 Too Many Requests`

When Freshdesk responds with:

```text
429 Too Many Requests
```

the connector:

1. reads `Retry-After` when provided
2. waits for the requested interval
3. retries the upstream request
4. returns the successful result if the retry succeeds

Example demo metadata:

```json
{
  "meta": {
    "retries": 1,
    "upstream_calls": 2
  }
}
```

## 5xx Errors

Server-side failures use bounded exponential backoff with jitter.

Configured behavior:

```text
Initial interval: 500 ms
Multiplier:       2.0
Maximum retries:  3
```

## Non-retryable Client Errors

The connector does not retry errors such as:

```text
401 Unauthorized
403 Forbidden
404 Not Found
```

Retrying these errors would generally create unnecessary upstream traffic without resolving the underlying problem.

---

# 10. Error Mapping

Upstream failures are converted into structured MCP/JSON-RPC errors.

| Upstream condition      | MCP code | Meaning                                     |
| ----------------------- | -------: | ------------------------------------------- |
| `401 Unauthorized`      | `-32001` | Freshdesk authentication failure            |
| `403 Forbidden`         | `-32003` | Permission or policy failure                |
| `404 Not Found`         | `-32004` | Requested ticket does not exist             |
| `429` retries exhausted | `-32029` | Freshdesk rate limit could not be recovered |
| Invalid JSON-RPC        | `-32600` | Invalid JSON-RPC request                    |
| Invalid parameters      | `-32602` | Tool arguments failed validation            |

This keeps upstream implementation details from leaking directly into the MCP contract.

---

# 11. Configuration

The connector is configured through environment variables or Spring configuration.

| Variable                        | Default    | Description                       |
| ------------------------------- | ---------- | --------------------------------- |
| `SPRING_PROFILES_ACTIVE`        | `mock`     | Runtime profile                   |
| `SERVER_PORT`                   | `8080`     | HTTP server port                  |
| `SERVER_ADDRESS`                | `0.0.0.0`  | Server bind address               |
| `FRESHDESK_DOMAIN`              | `mock`     | Freshdesk domain/subdomain        |
| `FRESHDESK_API_KEY`             | `mock-key` | Freshdesk API key                 |
| `FRESHDESK_BASE_URL`            | empty      | Optional base URL override        |
| `FRESHDESK_ALLOW_PRIVATE_NOTES` | `false`    | Operator policy for private notes |

For real Freshdesk usage, credentials should be supplied through the deployment environment or a secrets manager rather than committed to source control.

---

# 12. Mock / Demo Mode

Mock mode is the default evaluation environment.

It provides:

* 60 synthetic Freshdesk tickets
* multiple ticket statuses
* ticket tags and types
* conversation threads
* private-note examples
* configurable HTTP faults

No external Freshdesk account is required.

## Start

```bash
docker compose up -d
```

## Run the demonstration

```bash
bash demo/demo.sh
```

The demo covers:

```text
1. initialize
2. notifications/initialized
3. tools/list
4. search_tickets
5. get_ticket
6. get_ticket_conversations
7. private-note policy
8. 429 retry
9. bounded pagination
```

See:

```text
demo/DEMO.md
```

for the complete evaluator talk track.

---

# 13. Fault Injection

The mock server exposes:

```text
POST /mock-admin/faults
```

This allows resilience behavior to be tested deterministically without depending on a real Freshdesk environment.

---

## Simulate a 429 Rate Limit

```bash
curl -X POST http://localhost:8080/mock-admin/faults \
  -H "Content-Type: application/json" \
  -d '{"arm_429": 1, "retry_after": 2}'
```

The next matching upstream request returns:

```text
429 Too Many Requests
Retry-After: 2
```

The connector waits and retries.

---

## Simulate 500 Errors

```bash
curl -X POST http://localhost:8080/mock-admin/faults \
  -H "Content-Type: application/json" \
  -d '{"arm_500": 3}'
```

This allows retry-exhaustion behavior to be tested.

---

## Reset Faults

```bash
curl -X POST http://localhost:8080/mock-admin/faults \
  -H "Content-Type: application/json" \
  -d '{"reset": true}'
```

---

# 14. Running Against Live Freshdesk

The connector can be configured against a real Freshdesk instance.

Example:

```bash
docker run -d \
  --name freshdesk-mcp \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_ADDRESS=0.0.0.0 \
  -e FRESHDESK_DOMAIN="yourcompany.freshdesk.com" \
  -e FRESHDESK_API_KEY="your_api_key_here" \
  -e FRESHDESK_ALLOW_PRIVATE_NOTES=false \
  freshdesk-connector:1.0.0
```

The connector uses Freshdesk API-key authentication through HTTP Basic Auth:

```text
username = API key
password = X
```

Real credentials should never be committed to the repository or embedded in Docker images.

---

# 15. Docker

The project uses a multi-stage Docker build.

### Build stage

```text
maven:3.9.6-eclipse-temurin-17
```

Used to compile and package the Spring Boot application.

### Runtime stage

```text
eclipse-temurin:17-jre-jammy
```

The runtime image contains the Java runtime rather than the Maven build environment.

The container is configured to run as an unprivileged user.

## Docker Compose

The included `docker-compose.yml` provides a zero-configuration evaluation environment:

```yaml
services:
  freshdesk-connector:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=mock
      - SERVER_ADDRESS=0.0.0.0
      - FRESHDESK_DOMAIN=mock
      - FRESHDESK_API_KEY=mock-key
      - FRESHDESK_ALLOW_PRIVATE_NOTES=false
    restart: unless-stopped
```

---

# 16. Testing Strategy

The project uses:

* JUnit 5
* Mockito
* WireMock

Run the test suite with:

```bash
docker run --rm \
  -v "${PWD}:/workspace" \
  -w /workspace \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test
```

## Unit Tests

### `CursorCodecTest`

Validates:

* cursor serialization
* cursor deserialization
* cursor integrity/tampering detection

### `FreshdeskQueryBuilderTest`

Validates:

* Freshdesk query construction
* supported search filters

### `RetryExecutorTest`

Validates:

* retry behavior
* backoff calculation
* jitter
* non-retryable errors

## Integration Tests

### `McpControllerIntegrationTest`

Tests:

* JSON-RPC handshake
* MCP tool discovery
* tool invocation
* request validation

### `WireMockIntegrationTest`

Tests real HTTP interaction scenarios including:

1. ticket retrieval
2. authentication headers
3. `429` + `Retry-After`
4. successful retry
5. `401 Unauthorized`
6. non-retryable upstream failures

---

# 17. Security and Data Governance

### Credential protection

Freshdesk credentials are constructed inside the client layer and are not intentionally included in application logs, diagnostics, or MCP error responses.

### Private-note policy

Private notes are disabled by default:

```text
FRESHDESK_ALLOW_PRIVATE_NOTES=false
```

Exposure requires both:

```text
operator policy = enabled
+
tool argument = include_private_notes=true
```

### Untrusted ticket content

Ticket subjects, descriptions, tags, and conversation content originate from an external system and should be treated as **untrusted data** by downstream agents.

The connector returns such content in explicit structured fields; the MCP client/agent remains responsible for treating the content as data rather than instructions.

### Bounded context size

Conversation retrieval is bounded and long conversation content can be truncated according to the configured response limits, reducing the risk of excessively large agent contexts.

---

# 18. Limitations and Trade-offs

The connector intentionally makes several trade-offs.

## 1. Search is not unrestricted full-text search

Freshdesk's search API does not provide the unrestricted full-text behavior that a general-purpose search engine would provide.

The connector therefore limits keyword evaluation to supported fields such as subject and tags.

**Trade-off:** predictable API behavior at the cost of broader semantic search.

---

## 2. Listing uses client-side status filtering

The connector needs to support status-filtered listing while maintaining deterministic traversal.

Therefore:

```text
Freshdesk pages
       ↓
client-side status filtering
       ↓
MCP response
```

**Trade-off:** additional upstream page reads may be required.

---

## 3. Five-page scan budget

A single `list_tickets` invocation can scan at most:

```text
5 upstream pages
500 tickets
```

**Trade-off:** sparse result sets may require multiple calls using the returned cursor.

This prevents a single agent request from causing an unbounded Freshdesk scan.

---

## 4. Stateless HTTP transport

The connector uses:

```text
POST /mcp
```

with JSON-RPC rather than a stateful streaming transport.

**Trade-off:** simpler deployment and horizontal scaling, while leaving session/streaming requirements outside this assignment's scope.

---

## 5. Read-only scope

The connector intentionally exposes read operations only.

It does not currently provide tools for:

* creating tickets
* updating tickets
* assigning tickets
* changing ticket status
* adding replies
* modifying Freshdesk configuration

This reduces the risk of unintended side effects from an AI agent.

---

# 19. Production Hardening

The current implementation is designed for the take-home evaluation and local/mock execution.

A production deployment would additionally benefit from:

### MCP authentication and authorization

The `/mcp` endpoint should be protected by an authenticated gateway or equivalent identity layer.

### Secret management

Freshdesk credentials should be stored in:

* a cloud secret manager
* Kubernetes secrets
* Vault
* or an equivalent managed solution

rather than environment configuration supplied manually.

### Centralized observability

Production deployment should include:

* request metrics
* latency metrics
* retry counts
* upstream status codes
* structured logs
* distributed tracing

while ensuring that ticket content and credentials are not accidentally logged.

### Distributed rate-limit coordination

For horizontally scaled deployments, retry/rate-limit state may need to be coordinated rather than maintained independently by each instance.

### Search/indexing layer

For larger Freshdesk installations, a dedicated search/indexing strategy could provide richer full-text and semantic retrieval without repeatedly scanning upstream pages.

### Stronger authorization

Private-note access should ideally be tied to the authenticated operator's identity and permissions rather than only a deployment-level environment flag.

---

# 20. Repository Structure

```text
freshdesk-connector/
│
├── demo/
│   ├── demo.sh
│   └── DEMO.md
│
├── src/
│   ├── main/
│   │   ├── java/.../
│   │   │   ├── client/
│   │   │   │   └── Freshdesk REST client,
│   │   │   │       retry executor and DTOs
│   │   │   │
│   │   │   ├── mock/
│   │   │   │   └── In-process Freshdesk mock
│   │   │   │       and fault injection
│   │   │   │
│   │   │   ├── model/
│   │   │   │   └── MCP domain models and
│   │   │   │       JSON-RPC envelopes
│   │   │   │
│   │   │   ├── service/
│   │   │   │   └── Ticket service and
│   │   │   │       pagination cursor logic
│   │   │   │
│   │   │   └── tool/
│   │   │       └── MCP controller and
│   │   │           tool schema definitions
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-mock.yml
│   │
│   └── test/
│       └── Unit and integration tests
│
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

# 21. Manual Verification

## Verify the service

```bash
curl http://localhost:8080
```

## Verify MCP initialization

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {}
  }'
```

Expected structure:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": false
      }
    },
    "serverInfo": {
      "name": "freshdesk-connector",
      "version": "1.0.0"
    }
  }
}
```

## Verify tool discovery

Send:

```text
tools/list
```

The response should expose:

```text
search_tickets
list_tickets
get_ticket
get_ticket_conversations
```

---

# 22. Troubleshooting

## Port 8080 is already in use

Check:

```bash
docker ps
```

or change:

```text
SERVER_PORT
```

and the Docker port mapping.

---

## Container does not start

Check:

```bash
docker compose logs -f
```

Verify:

```text
SPRING_PROFILES_ACTIVE=mock
SERVER_ADDRESS=0.0.0.0
```

---

## MCP endpoint is unreachable

Verify that the container is running:

```bash
docker compose ps
```

Then:

```bash
curl http://localhost:8080/mcp
```

For JSON-RPC requests, use:

```text
POST /mcp
Content-Type: application/json
```

---

## Demo script fails

Restart the mock environment:

```bash
docker compose down
docker compose up -d
```

Then rerun:

```bash
bash demo/demo.sh
```

If a fault was manually injected, reset it:

```bash
curl -X POST http://localhost:8080/mock-admin/faults \
  -H "Content-Type: application/json" \
  -d '{"reset": true}'
```

---

# 23. Design Summary

The connector intentionally keeps the MCP surface small:

```text
search_tickets
      │
      ├── targeted discovery
      │
list_tickets
      │
      ├── deterministic traversal
      │
get_ticket
      │
      ├── single-ticket retrieval
      │
get_ticket_conversations
      │
      └── explicit conversation access
```

Around these tools, the implementation adds four important controls:

```text
              ┌─────────────────────┐
              │    MCP Tool Layer   │
              └──────────┬──────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    Bounded Search   Privacy Policy   Validation
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                  Freshdesk Client
                         │
                 ┌───────┴────────┐
                 ▼                ▼
             Pagination        Resilience
                              + Retry/429
```

The result is a small, read-only Freshdesk connector with explicit retrieval boundaries, predictable upstream usage, and a demo environment that allows the complete behavior to be evaluated without real customer data or credentials.

This version is intentionally **less bloated than the original 482-line README**, while still documenting the architecture, API behavior, security decisions, demo, testing, deployment, limitations, and production-hardening path an evaluator is likely to care about.
