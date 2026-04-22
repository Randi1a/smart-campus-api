package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.DataStore;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // GET /api/v1/sensors/{sensorId}/readings
    @GET
    public Response getReadings() {
        Sensor sensor = DataStore.getSensor(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor not found: " + sensorId)).build();
        }
        List<SensorReading> history = DataStore.getReadings(sensorId);
        return Response.ok(history).build();
    }

    // POST /api/v1/sensors/{sensorId}/readings
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = DataStore.getSensor(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor not found: " + sensorId)).build();
        }
        // Block if sensor is in MAINTENANCE
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensorId + "' is currently under MAINTENANCE and cannot accept readings."
            );
        }
        SensorReading newReading = new SensorReading(reading.getValue());
        DataStore.addReading(sensorId, newReading);  // also updates sensor.currentValue

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "message", "Reading recorded",
                        "readingId", newReading.getId(),
                        "updatedCurrentValue", sensor.getCurrentValue()
                )).build();
    }
}