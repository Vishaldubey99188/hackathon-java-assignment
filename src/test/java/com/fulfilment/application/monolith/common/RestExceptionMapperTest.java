package com.fulfilment.application.monolith.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RestExceptionMapperTest {

  @Inject RestExceptionMapper mapper;

  @Test
  public void testClientErrorIsMappedWithOriginalStatusCode() {
    WebApplicationException exception = new WebApplicationException("Not found", 404);

    Response response = mapper.toResponse(exception);

    assertEquals(404, response.getStatus());
    assertTrue(response.getEntity().toString().contains("Not found"));
  }

  @Test
  public void testUnexpectedExceptionIsMappedTo500() {
    RuntimeException exception = new RuntimeException("Something broke");

    Response response = mapper.toResponse(exception);

    assertEquals(500, response.getStatus());
    assertTrue(response.getEntity().toString().contains("Something broke"));
  }
}
