package com.smartcampus.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger log = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable e) {
        // Let JAX-RS handle its own exceptions (404, 405, 415 etc.) normally
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }

        // Only catch truly unexpected errors
        log.severe("Unexpected error: " + e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "status", 500,
                        "error", "Internal Server Error",
                        "message", "An unexpected error occurred. Please contact the administrator."
                )).build();
    }
}