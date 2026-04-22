# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey 3)** and an embedded **Grizzly HTTP server**, developed for the 5COSC022W Client-Server Architectures coursework. The API manages campus Rooms and Sensors, tracks historical sensor readings, and demonstrates RESTful design principles including sub-resource locators, exception mapping, and request/response logging.

---

## API Design Overview

The API follows a **hierarchical resource model** that mirrors the physical structure of a smart campus:

```
/api/v1
├── /rooms
│   ├── GET    — list all rooms
│   ├── POST   — create a room
│   └── /{roomId}
│       ├── GET    — fetch a specific room
│       └── DELETE — decommission a room (blocked if sensors are present)
├── /sensors
│   ├── GET    — list all sensors (optional ?type= filter)
│   ├── POST   — register a sensor (validates roomId exists)
│   └── /{sensorId}/readings
│       ├── GET  — fetch reading history
│       └── POST — submit a new reading (updates sensor's currentValue)
```

**Key design decisions:**
- **In-memory storage** using `ConcurrentHashMap` for thread-safe room and sensor data, and `CopyOnWriteArrayList` for each room's sensor ID list.
- **Sub-resource locator pattern** delegates reading management to a dedicated `SensorReadingResource` class.
- **Custom exception mappers** ensure the API never leaks stack traces — all errors return structured JSON.
- **JAX-RS filters** provide cross-cutting request/response logging without polluting resource classes.

---

## How to Build & Run

### Prerequisites
- Java 17 or higher
- Apache Maven 3.6+

### Steps

**1. Clone the repository**
```bash
git clone https://github.com/Randi1a/smart-campus-api.git
cd smart-campus-api
```

**2. Build the fat JAR**
```bash
mvn clean package
```

**3. Start the server**
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

The server starts on:
```
http://localhost:8080/api/v1/
```

Press **ENTER** in the terminal to stop the server gracefully.

---

## Sample curl Commands

### 1. Discovery — GET /api/v1/
```bash
curl -s http://localhost:8080/api/v1/ | python -m json.tool
```
Returns API metadata: version, contact, and resource links.

---

### 2. Create a Room — POST /api/v1/rooms
```bash
curl -s -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":50}' \
  | python -m json.tool
```
Returns `201 Created` with a `Location` header pointing to the new room.

---

### 3. List All Rooms — GET /api/v1/rooms
```bash
curl -s http://localhost:8080/api/v1/rooms | python -m json.tool
```

---

### 4. Register a Sensor — POST /api/v1/sensors
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-001","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}' \
  | python -m json.tool
```
Returns `201 Created`. Fails with `422 Unprocessable Entity` if the `roomId` does not exist.

---

### 5. Filter Sensors by Type — GET /api/v1/sensors?type=CO2
```bash
curl -s "http://localhost:8080/api/v1/sensors?type=CO2" | python -m json.tool
```
Returns only sensors whose type matches `CO2` (case-insensitive).

---

### 6. Submit a Sensor Reading — POST /api/v1/sensors/{sensorId}/readings
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":412.5}' \
  | python -m json.tool
```
Returns `201 Created` with the new reading ID and the sensor's updated `currentValue`. Blocked with `403 Forbidden` if the sensor status is `MAINTENANCE`.

---

### 7. View Reading History — GET /api/v1/sensors/{sensorId}/readings
```bash
curl -s http://localhost:8080/api/v1/sensors/CO2-001/readings | python -m json.tool
```

---

### 8. Delete a Room (success) — DELETE /api/v1/rooms/{roomId}
```bash
curl -s -X DELETE http://localhost:8080/api/v1/rooms/LIB-301 | python -m json.tool
```
Returns `200 OK`. Returns `409 Conflict` if the room still has sensors assigned.

---

## Report — Answers to Coursework Questions

### Part 1.1 — JAX-RS Resource Lifecycle & In-Memory Data

By default, JAX-RS creates a **new instance of every resource class for each incoming HTTP request** (request-scoped lifecycle). This is the out-of-the-box behaviour of frameworks like Jersey and is intentional: it keeps resource objects lightweight, stateless, and free from instance-variable concurrency issues.

However, this creates an important architectural constraint for managing **shared, in-memory state**. If the data store were held as an instance variable on the resource class, each request would get its own private copy — data written by one request would be invisible to the next. To avoid this, all shared state must live in a **static, class-level structure** that survives across request instances.

In this implementation, `DataStore` is a utility class with `static` fields backed by `ConcurrentHashMap` (for rooms, sensors, and readings). `ConcurrentHashMap` is thread-safe by design: it segments its internal buckets so that concurrent reads and writes do not block each other and do not require explicit `synchronized` blocks. This prevents race conditions when multiple requests arrive simultaneously — for example, two clients registering different sensors at the same instant.

For `Room.sensorIds`, a `CopyOnWriteArrayList` is used. On every write (add/remove), this structure creates a fresh copy of the underlying array. This means concurrent reads always see a consistent snapshot and never throw `ConcurrentModificationException` during iteration. The trade-off is slightly higher write cost, which is acceptable given that reads (list/GET) are far more frequent than writes (POST/DELETE) in this system.

---

### Part 1.2 — HATEOAS and Hypermedia in RESTful APIs

**HATEOAS** (Hypermedia As The Engine Of Application State) is the principle that API responses should include not just data, but also **links to related actions and resources** — guiding clients through what they can do next without consulting external documentation.

This is considered a hallmark of advanced REST because it elevates an API from a static collection of endpoints to a **self-documenting, navigable interface**. For example, a response to `GET /api/v1` that includes `"rooms": "/api/v1/rooms"` allows a client to discover and navigate the API purely from the root response, the same way a web browser follows hyperlinks on a page.

For client developers, this has significant practical benefits. With static documentation, the developer must manually keep their code in sync with the API specification — if an endpoint URL changes, every client that hard-coded that URL breaks silently. With HATEOAS, the client follows links returned by the server, making it resilient to URL restructuring. It also reduces the learning curve: a developer can explore the API interactively by following links, rather than reading pages of documentation before writing a single line of code.

---

### Part 2.1 — Returning IDs Only vs. Full Room Objects

When a client requests `GET /api/v1/rooms`, the service must decide what each item in the list contains.

**Returning only IDs** (e.g., `["LIB-301", "LAB-202"]`) minimises payload size, which matters at scale — if a campus has thousands of rooms, transferring full objects wastes bandwidth. However, it forces the client to issue a separate `GET /api/v1/rooms/{id}` request for every room it needs details about (the N+1 problem), which can have serious latency implications over poor networks.

**Returning full room objects** increases payload size but allows the client to render a complete list in a single round-trip. This is the approach taken in this implementation. For a campus management dashboard that needs to display room names, capacities, and sensor counts, the full-object approach eliminates unnecessary follow-up requests and reduces perceived latency for end users.

The correct choice depends on the client's use case and network context. A best-practice compromise — used by APIs like GitHub — is to return a **summary representation** containing the most commonly needed fields, with a `links` object pointing to the full detail endpoint for richer data.

---

### Part 2.2 — Idempotency of DELETE

An operation is **idempotent** if applying it multiple times produces the same result as applying it once.

In this implementation, the first `DELETE /api/v1/rooms/{roomId}` on a valid room responds with `200 OK` and removes the room from the store. Any **subsequent identical DELETE request** for the same ID finds no room in the store and returns `404 Not Found`.

This means the DELETE operation in this API is **not strictly idempotent** in terms of HTTP status codes — the response changes from `200` to `404` on repeated calls. However, the **server state is idempotent**: after the first successful DELETE, no amount of repeated calls will re-delete the room or corrupt any data. The resource simply does not exist. REST's definition of idempotency concerns the state of the server, not the response code, so DELETE is generally considered idempotent by the HTTP specification (RFC 9110) even when repeated calls return 404.

---

### Part 3.1 — Consequences of a Content-Type Mismatch with @Consumes

When a resource method is annotated with `@Consumes(MediaType.APPLICATION_JSON)`, JAX-RS registers that method as only accepting requests with a `Content-Type: application/json` header.

If a client sends a request with `Content-Type: text/plain` or `Content-Type: application/xml`, JAX-RS intercepts the request **before it reaches the resource method**. It cannot find a matching method candidate for the supplied media type and immediately returns an **HTTP 415 Unsupported Media Type** response. No resource code executes, and no partial processing occurs.

This is a deliberate contract-enforcement mechanism. It prevents malformed or mis-typed data from reaching the deserialization layer (Jackson, in this project). Without `@Consumes`, JAX-RS would attempt to deserialize the raw `text/plain` body as if it were JSON, likely throwing a `JsonParseException` — which would then bubble up as a `500 Internal Server Error` without a custom mapper, leaking implementation details to the client.

---

### Part 3.2 — @QueryParam vs. Path Parameter for Filtering

Two design approaches exist for filtering a sensor list by type:

- **Query parameter:** `GET /api/v1/sensors?type=CO2`
- **Path parameter:** `GET /api/v1/sensors/type/CO2`

The query parameter approach is superior for several reasons:

1. **Optionality:** A query parameter is naturally optional. `GET /api/v1/sensors` and `GET /api/v1/sensors?type=CO2` use the same endpoint, with the filter being additive. A path-based design requires a separate route for the unfiltered case, introducing duplication.

2. **Semantics:** A URL path should identify a **resource**. `/api/v1/sensors/CO2` implies `CO2` is a sensor ID, which is misleading and collides with the `/{sensorId}/readings` sub-resource locator. Query strings are semantically reserved for **search, filter, and sort** operations on a collection.

3. **Composability:** Query parameters compose naturally — `?type=CO2&status=ACTIVE` adds a second filter trivially. Achieving the same with path segments requires explicit route design for every combination.

4. **Cacheability and bookmarkability:** Standard HTTP caching infrastructure (CDNs, proxies) understands query strings as part of the resource identifier, handling filtered and unfiltered responses as distinct cache entries.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern allows a resource class method to **delegate handling of a URL prefix** to a separate class, rather than defining every nested endpoint in the same file.

In this implementation, `SensorResource` contains:
```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

JAX-RS sees that this method has no HTTP verb annotation and treats it as a locator — it calls the method to obtain an instance of `SensorReadingResource`, then dispatches the remaining HTTP verb (`GET` or `POST`) to that class.

**Architectural benefits:**

- **Separation of concerns:** `SensorResource` handles sensor-level operations; `SensorReadingResource` handles reading-level operations. Each class has a single responsibility.
- **Maintainability:** In a large API with dozens of resource types and several levels of nesting, putting all paths in one controller creates a file with hundreds of methods that is difficult to navigate, test, and extend. Delegation keeps each class focused and of manageable size.
- **Reusability:** `SensorReadingResource` could theoretically be reused from multiple locators if the data model warranted it.
- **Testability:** Each class can be unit-tested in isolation without instantiating the entire resource hierarchy.

---

### Part 5.2 — Cybersecurity Risks of Exposing Stack Traces

Exposing a raw Java stack trace in an API response is a significant security vulnerability. An attacker can extract the following from a typical stack trace:

1. **Internal file paths and package structure** — reveals the application's source layout, making it easier to craft targeted exploits or understand business logic.
2. **Library names and versions** — for example, `com.fasterxml.jackson.databind` at a specific version. The attacker can look up known CVEs for that exact version and attempt to exploit them.
3. **Framework internals** — Jersey/Grizzly version details narrow down which server-side vulnerabilities may apply.
4. **Business logic clues** — method names in the stack (e.g., `processPayment`, `validateAdminToken`) hint at internal workflows, potentially identifying injection points or privilege-escalation paths.
5. **Database or persistence layer details** — if a query-related exception propagates, it may expose table names, column names, or SQL dialect.

This is classified under **CWE-209: Generation of Error Message Containing Sensitive Information**. The `GlobalExceptionMapper` in this project eliminates this risk by catching all `Throwable` types and returning only a generic `500 Internal Server Error` message to the client, while logging the full detail server-side where only authorised personnel can access it.

---

### Part 5.3 — JAX-RS Filters vs. Manual Logging in Resource Methods

Manually inserting `Logger.info()` calls into every resource method is an example of a **cross-cutting concern** handled inline, which has significant drawbacks.

**Duplication:** With ten resource methods, there are ten places to write and maintain the same log statements. If the log format changes, ten edits are needed — and it is easy to miss one, creating inconsistent logs.

**Coupling:** Resource classes accumulate responsibilities. A class whose purpose is to handle HTTP requests should not also be responsible for observability infrastructure.

**Risk of omission:** A developer adding a new resource method may forget to add logging, creating silent blind spots in production monitoring.

By implementing `ContainerRequestFilter` and `ContainerResponseFilter` in a single `@Provider`-annotated class, logging is applied **automatically and uniformly** to every request and response passing through the JAX-RS runtime — including endpoints added in the future — without changing a single resource class. This is the principle of **Aspect-Oriented Programming** applied via the JAX-RS filter chain: cross-cutting concerns are isolated in one place, keeping resource classes focused on business logic alone.

---
