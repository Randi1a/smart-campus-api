package com.smartcampus.store;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
    private static final Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();

    // ── Rooms ──────────────────────────────────────────────
    public static Map<String, Room> getRooms() { return rooms; }

    public static Room getRoom(String id) { return rooms.get(id); }

    public static void addRoom(Room room) { rooms.put(room.getId(), room); }

    public static boolean roomExists(String id) { return rooms.containsKey(id); }

    public static boolean deleteRoom(String id) {
        Room room = rooms.remove(id);
        if (room == null) {
            return false;
        }
        // Cascade: remove all sensors and their readings that belonged to this room
        for (String sensorId : room.getSensorIds()) {
            sensors.remove(sensorId);
            readings.remove(sensorId);
        }
        return true;
    }

    // ── Sensors ────────────────────────────────────────────
    public static Map<String, Sensor> getSensors() { return sensors; }

    public static Sensor getSensor(String id) { return sensors.get(id); }

    public static void addSensor(Sensor sensor) {
        sensors.put(sensor.getId(), sensor);
        readings.put(sensor.getId(), new ArrayList<>());
        // Link sensor to its parent room atomically with registration
        Room room = rooms.get(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().add(sensor.getId());
        }
    }

    public static boolean sensorExists(String id) { return sensors.containsKey(id); }

    // ── Readings ───────────────────────────────────────────
    public static List<SensorReading> getReadings(String sensorId) {
        return readings.getOrDefault(sensorId, new ArrayList<>());
    }

    public static void addReading(String sensorId, SensorReading reading) {
        readings.computeIfAbsent(sensorId, k -> new ArrayList<>()).add(reading);
        // Side effect: update parent sensor's currentValue
        Sensor sensor = sensors.get(sensorId);
        if (sensor != null) {
            sensor.setCurrentValue(reading.getValue());
        }
    }
}