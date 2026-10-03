# Read Me First
The following was discovered as part of building this project:

* No Docker Compose services found. As of now, the application won't start! Please add at least one service to the `compose.yaml` file.
* The original package name 'freshdesk-connector.razorpay-assignment' is invalid and this project uses 'freshdesk_connector.razorpay_assignment' instead.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.1.1/reference/features/dev-services.html#features.dev-services.docker-compose)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Docker Compose support
This project contains a Docker Compose file named `compose.yaml`.

However, no services were found. As of now, the application won't start!

Please make sure to add at least one service in the `compose.yaml` file.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.



**Key Architectural Invariants:**
* **Strict Layer Separation:** Upstream DTOs (`FdTicket`, `FdConversation`) are package-private or restricted to `client/` and never escape to the service or tool layers.
* **Domain Shaping:** The service layer maps external DTOs into clean domain representations (`Ticket`, `Conversation`).
* **Protocol Uniformity:** Errors occurring at the upstream or application layer return as tool errors (`isError: true` with code, message, and metadata) rather than breaking the JSON-RPC channel.

---

### 2. Freshdesk API Verification & Constraints
The implementation honors the following verified constraints of the Freshdesk API:
1. **Authentication:** HTTP Basic authentication where the API key is passed as username with a dummy password (`-u apikey:X`).
2. **Rate Limits & Retry-After:** Response headers `X-Ratelimit-Total`, `X-Ratelimit-Remaining`, and `Retry-After` (in seconds). Limits are account-wide.
3. **No Free-Text Search:** Freshdesk's `/api/v2/search/tickets` only supports field-based queries (`status`, `priority`, `tag`, `type`, `created_at`). Free-text keyword filtering over subject and tags is implemented client-side over server-filtered candidates.
4. **Hard Search Ceiling:** Freshdesk search results are strictly bounded to 30 tickets per page and a maximum of 10 pages (300 candidates maximum).
5. **Listing Time Window:** By default, `/api/v2/tickets` only returns tickets created within the last 30 days unless `updated_since` is explicitly supplied.

---

### 3. MCP Interface Specification
The connector exposes a stateless JSON-RPC 2.0 endpoint at `POST /mcp`:
* `initialize`: Negotiates protocol version `2024-11-05`, announces tools capabilities.
* `notifications/initialized`: Responds with HTTP `202 Accepted`.
* `ping`: Liveness check.
* `tools/list`: Emits definitions and JSON Schemas for all 4 tools.
* `tools/call`: Executes the requested tool with structured arguments.

#### Available Tools:
1. `search_tickets`: Filter-driven candidate discovery with client-side keyword matching.
2. `list_tickets`: Deterministic paginated ticket browsing.
3. `get_ticket`: Single ticket detail extraction (never includes conversation embeds).
4. `get_ticket_conversations`: Ticket conversation thread extraction (private notes excluded by default).

---

### 4. Dual Retrieval Paths (Listing vs. Search)
To avoid mixing semantics, the service layer separates reads into two distinct strategies:
* **`ListingStrategy` (`GET /api/v2/tickets`):** Used for chronological, stable browsing. Always requests `order_by=created_at&order_type=desc&per_page=100`.
* **`SearchStrategy` (`GET /api/v2/search/tickets`):** Used for multi-attribute discovery (e.g. status + priority + tags + keywords). Subject to the 300 candidate upstream ceiling.

Cursors are tagged by strategy type (`list` vs `conv`). A cursor generated from listing cannot be passed to search.

---

### 5. 5-Page / 500-Ticket Bounded Pagination
Freshdesk's `/tickets` endpoint has no server-side filter for `status`. Consequently, `list_tickets(status=...)` must evaluate status client-side across ordered pages.

**The Bounded Scan Trade-off:**
* If a status is sparse (e.g. only 2 tickets in the last 400 match `status: pending`), fetching a page of 25 matching tickets could consume excessive API calls.
* The connector bounds upstream page traversal to a maximum of **5 upstream pages (500 tickets)** per tool invocation (`max-pages-per-call: 5`).
* Every response returns a `scan` metadata object:
  ```json
  "scan": {
    "upstream_pages_fetched": 5,
    "tickets_scanned": 500,
    "max_pages_budget": 5
  }

