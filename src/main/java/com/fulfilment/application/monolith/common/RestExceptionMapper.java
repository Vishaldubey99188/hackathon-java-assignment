package com.fulfilment.application.monolith.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Single application-wide exception mapper for all REST resources (Warehouse, Product, Store).
 * Client errors (4xx) are expected, routine outcomes and are logged at DEBUG; anything mapping to
 * 5xx is an actual failure and is logged at ERROR with the full stack trace.
 */
@Provider
public class RestExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger LOGGER = Logger.getLogger(RestExceptionMapper.class.getName());

  @Inject ObjectMapper objectMapper;

  @Override
  public Response toResponse(Exception exception) {
    int code = 500;
    if (exception instanceof WebApplicationException) {
      code = ((WebApplicationException) exception).getResponse().getStatus();
    }

    if (code >= 500) {
      LOGGER.error("Failed to handle request", exception);
    } else {
      LOGGER.debugf(exception, "Request rejected with status %d: %s", code, exception.getMessage());
    }

    ObjectNode exceptionJson = objectMapper.createObjectNode();
    exceptionJson.put("exceptionType", exception.getClass().getName());
    exceptionJson.put("code", code);

    if (exception.getMessage() != null) {
      exceptionJson.put("error", exception.getMessage());
    }

    return Response.status(code).entity(exceptionJson).build();
  }
}
