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

By default, JAX-RS is set up to create a brand new instance of a resource class every time an HTTP request comes in. This is called a request-scoped lifecycle. It's different from a `@Singleton` lifecycle, where one single instance handles every single request. While a singleton can cause major headaches with thread-safety if you use instance variables, the request-scoped approach keeps things lightweight and avoids those concurrency issues.

However, this creates a challenge for our in-memory data. If we stored our rooms and sensors as normal instance variables, each request would get its own blank slate, and any data saved wouldn't be visible to the next request. To fix this, all of our shared state is kept in a static, class-level structure so it survives across different requests.

In my project, `DataStore` acts as a utility class with static fields that use `ConcurrentHashMap`. I chose `ConcurrentHashMap` because it's thread-safe right out of the box. It divides its internal data into buckets, which means reading and writing at the same time won't block each other or require messy `synchronized` blocks. This stops race conditions—like two users trying to register sensors at the exact same time.

For the list of sensors in each room (`Room.sensorIds`), I used a `CopyOnWriteArrayList`. Every time we add or remove a sensor, it creates a fresh copy of the array. This guarantees that anyone reading the list gets a consistent view and won't run into a `ConcurrentModificationException`. It does make writing a bit slower, but that's a fair trade-off since we'll be reading (GET requests) much more often than writing (POST/DELETE) in this API.

---

### Part 1.2 — HATEOAS and Hypermedia in RESTful APIs

HATEOAS (Hypermedia As The Engine Of Application State) is a REST principle where the API doesn't just return data, but also includes links to related actions. It basically guides the client on what they can do next without needing to constantly check external documentation.

This is what makes an API truly RESTful. Instead of just being a static set of endpoints, it becomes a self-documenting, easy-to-navigate interface. For example, when you call `GET /api/v1`, it returns something like `"rooms": "/api/v1/rooms"`. This lets a client application discover the API starting from just the root URL, much like how a user browses a website by clicking links.

For developers building the client application, this is a huge help. With standard static docs, developers have to hard-code URLs. If the API changes a URL later, the client app breaks silently. By using HATEOAS, the client just follows the links provided by the server, making the app much more resilient to future changes. It also makes learning the API much easier, since developers can interactively explore it instead of reading pages of documentation first.

---

### Part 2.1 — Returning IDs Only vs. Full Room Objects

When a user calls `GET /api/v1/rooms`, we have to decide how much data to send back for each room.

Sending back just the IDs (like `["LIB-301", "LAB-202"]`) keeps the payload very small. This is great for saving bandwidth, especially if the campus has thousands of rooms. The downside is that if the client actually needs the room details, it has to make a separate `GET /api/v1/rooms/{id}` request for every single room. This is known as the N+1 problem, and it can cause terrible lag on slower networks.

On the other hand, returning the full room objects makes the payload larger, but it lets the client get everything it needs in just one trip. I went with this approach for my project. If we imagine a dashboard that needs to show room names, capacities, and how many sensors they have, sending full objects stops the client from having to make endless follow-up requests, which makes the app feel much faster for the end user.

The best choice really depends on the situation. A common middle-ground (used by big APIs like GitHub) is to return a summary object with just the most important fields, along with a link to fetch the full details if needed.

---

### Part 2.2 — Idempotency of DELETE

An operation is considered idempotent if doing it multiple times gives the same end result as doing it just once.

In my API, if you send a `DELETE /api/v1/rooms/{roomId}` request for a room that exists, it returns a `200 OK` and deletes it. If you accidentally send that exact same DELETE request again, the server won't find the room and will return a `404 Not Found`.

Because the status code changes from `200` to `404`, the response itself isn't strictly idempotent. However, the actual state of the server is. After that first successful delete, sending more delete requests won't accidentally delete something else or corrupt any data—the room is just gone. Since the REST definition of idempotency is really about the server's state and not the HTTP response code, the DELETE method is still considered idempotent according to HTTP standards (RFC 9110).

---

### Part 3.1 — Consequences of a Content-Type Mismatch with @Consumes

By adding the `@Consumes(MediaType.APPLICATION_JSON)` annotation to a method, we're telling JAX-RS that this endpoint will only accept requests if the `Content-Type` header is set to `application/json`.

If someone tries to send a request with `Content-Type: text/plain` instead, JAX-RS actually stops the request before it even reaches my Java method. It sees that the media type doesn't match and immediately fires back a `415 Unsupported Media Type` error. This means none of my code runs, which is exactly what we want.

This acts as a strict safety net. It stops badly formatted data from reaching the Jackson deserializer. If we didn't use `@Consumes`, JAX-RS might try to parse plain text as JSON, which would crash and throw a `JsonParseException`. That would usually result in a messy `500 Internal Server Error`, which looks unprofessional and might leak details about how our code works.

---

### Part 3.2 — @QueryParam vs. Path Parameter for Filtering

When we want to filter sensors by their type, there are two main ways to design the URL:

- **Using a query parameter:** `GET /api/v1/sensors?type=CO2`
- **Using a path parameter:** `GET /api/v1/sensors/type/CO2`

I chose the query parameter approach because it's much better for this scenario:

1. **It's optional:** Query parameters are naturally optional. We can use `GET /api/v1/sensors` to get everything, and just tack on `?type=CO2` to filter it. If we used path parameters, we'd have to write completely separate routes for the filtered and unfiltered versions.
2. **It makes semantic sense:** In REST, the URL path is supposed to point to a specific resource. A path like `/api/v1/sensors/CO2` makes it look like `CO2` is the ID of a sensor, which is confusing. Query strings are the standard way to handle searching, filtering, and sorting a collection.
3. **It's easy to combine:** If we later want to filter by status too, query strings make it easy: `?type=CO2&status=ACTIVE`. Doing that with path segments gets really messy really fast.
4. **Caching:** Standard web caches and proxies easily recognize query strings as part of the URL, so they can properly cache the filtered results separately from the full list.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern is a clever way to keep code organized. Instead of putting every single nested endpoint inside one massive class, we can have a method that delegates part of the URL to another class entirely.

In my project, `SensorResource` has this method:
```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

Notice that there's no `@GET` or `@POST` here. JAX-RS sees this and realizes it's a locator. It runs the method to get a new `SensorReadingResource` object, and then passes the rest of the request over to that new class to handle.

This has a few big architectural benefits:

- **Separation of concerns:** `SensorResource` only deals with sensor stuff, while `SensorReadingResource` only deals with reading history. Each class has one clear job.
- **Easier to maintain:** In a massive API, putting all the routes in one file creates a giant, unreadable mess. Breaking it up makes the codebase much easier to navigate and update.
- **Testing:** We can unit test the reading logic completely independently without having to set up the parent sensor logic at the same time.

---

### Part 5.1 — 422 Unprocessable Entity vs. 404 Not Found for Payload Reference Issues

If a user tries to register a new sensor but provides a `roomId` that doesn't actually exist in the system, we need to return an error. A lot of developers default to returning a `404 Not Found` here, but that's actually an anti-pattern. A `404` technically means the URL itself (like `/api/v1/sensors`) doesn't exist, which isn't true—the URL is perfectly fine.

The real problem is inside the request body (the bad `roomId`). For this, `422 Unprocessable Entity` is the most accurate choice. It tells the client: "I understand the JSON you sent, and the syntax is fine, but I can't process it because the data itself has semantic errors (like a bad foreign key)." This gives the client developer a very clear signal that they hit the right endpoint, but their payload data is wrong.

---

### Part 5.2 — Cybersecurity Risks of Exposing Stack Traces

If an API crashes and sends a raw Java stack trace back to the user, it's a huge security risk. Hackers can read those stack traces and learn a lot about the system, such as:

1. **Internal file paths:** It shows exactly how the project folders are structured, which helps attackers map out the application.
2. **Library versions:** The stack trace might show that we're using a specific version of Jackson or Jersey. If there are known vulnerabilities (CVEs) for those exact versions, the attacker now knows exactly what to target.
3. **Business logic:** Method names in the trace (like `validateAdminToken`) can accidentally reveal how the system works behind the scenes, showing attackers where to look for weak spots.
4. **Database details:** If a database error leaks out, it could expose table names or column names, making SQL injection attacks much easier.

In the security world, this is known as CWE-209 (Generation of Error Message Containing Sensitive Information). To stop this, I built a `GlobalExceptionMapper`. It acts as a safety net that catches absolutely any `Throwable` error, logs the full details safely on the server side, and only sends a generic `500 Internal Server Error` message back to the user.

---

### Part 5.3 — JAX-RS Filters vs. Manual Logging in Resource Methods

If we wanted to log every request, the naive approach would be to manually type `Logger.info()` at the start of every single resource method. But this has some major problems.

First, it creates a lot of duplicated code. If we have ten endpoints, we have to write the same logging code ten times. If we want to change what gets logged, we have to update all ten places. Second, it clutters the code. A method that handles HTTP requests shouldn't also have to worry about the infrastructure of logging. Finally, there's the risk of human error—someone might add a new endpoint and simply forget to include the logging code.

Instead, I used JAX-RS filters (`ContainerRequestFilter` and `ContainerResponseFilter`). By putting these in a single class with the `@Provider` annotation, JAX-RS automatically applies our logging to every single request and response that goes through the server. Even if we add new endpoints later, they'll get logged automatically without us having to change any of the resource classes. This keeps the core business logic totally clean and focused.
