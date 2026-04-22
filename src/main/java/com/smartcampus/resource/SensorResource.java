package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Sensor;
import com.smartcampus.store.DataStore;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    // GET /api/v1/sensors  or  /api/v1/sensors?type=CO2
    @GET
    public Response getSensors(@QueryParam("type") String type) {
        Collection<Sensor> all = DataStore.getSensors().values();
        if (type != null && !type.isBlank()) {
            List<Sensor> filtered = all.stream()
                    .filter(s -> s.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
            return Response.ok(filtered).build();
        }
        return Response.ok(all).build();
    }

    // POST /api/v1/sensors — register a new sensor
    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Sensor ID is required")).build();
        }
        // Validate roomId exists
        if (sensor.getRoomId() == null || !DataStore.roomExists(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                    "Room with ID '" + sensor.getRoomId() + "' does not exist. Cannot register sensor."
            );
        }
        if (DataStore.sensorExists(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Sensor with this ID already exists")).build();
        }
        DataStore.addSensor(sensor);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "Sensor registered successfully", "id", sensor.getId()))
                .build();
    }

    // Sub-resource locator: /api/v1/sensors/{sensorId}/readings
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}