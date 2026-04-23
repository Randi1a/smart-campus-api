package com.smartcampus.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("api", "Smart Campus Sensor & Room Management API");
        info.put("version", "1.0");
        info.put("description", "RESTful API for managing campus rooms, IoT sensors, and sensor readings.");
        info.put("contact", "admin@smartcampus.ac.uk");

        // HATEOAS-style resource map: each entry advertises its href, supported HTTP methods,
        // and a human-readable description so clients can navigate the API without static docs.
        Map<String, Object> roomsLink = new LinkedHashMap<>();
        roomsLink.put("href", "/api/v1/rooms");
        roomsLink.put("methods", List.of("GET", "POST"));
        roomsLink.put("description", "List all rooms or register a new room.");

        Map<String, Object> roomByIdLink = new LinkedHashMap<>();
        roomByIdLink.put("href", "/api/v1/rooms/{roomId}");
        roomByIdLink.put("methods", List.of("GET", "DELETE"));
        roomByIdLink.put("description", "Retrieve or delete a specific room by ID.");

        Map<String, Object> sensorsLink = new LinkedHashMap<>();
        sensorsLink.put("href", "/api/v1/sensors");
        sensorsLink.put("methods", List.of("GET", "POST"));
        sensorsLink.put("description", "List all sensors (optionally filter by ?type=) or register a new sensor.");

        Map<String, Object> readingsLink = new LinkedHashMap<>();
        readingsLink.put("href", "/api/v1/sensors/{sensorId}/readings");
        readingsLink.put("methods", List.of("GET", "POST"));
        readingsLink.put("description", "Retrieve reading history or record a new reading for a sensor.");

        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("rooms", roomsLink);
        resources.put("roomById", roomByIdLink);
        resources.put("sensors", sensorsLink);
        resources.put("sensorReadings", readingsLink);

        info.put("resources", resources);

        return Response.ok(info).build();
    }
}