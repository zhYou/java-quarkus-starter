package io.github.zhyou.starter.cli;

import com.fasterxml.jackson.databind.*;
import io.github.zhyou.starter.domain.OrderErrorCode;
import java.io.PrintWriter;
import java.util.Map;

public final class CliOutput {
  private final boolean json;
  private final PrintWriter out, err;
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public CliOutput(boolean json, PrintWriter out, PrintWriter err) {
    this.json = json;
    this.out = out;
    this.err = err;
  }

  public void writeSuccess(Object value) {
    if (json) printJson(value);
    else out.println(value);
    out.flush();
  }

  public void writeFailure(OrderErrorCode code, String message) {
    if (json) printJson(Map.of("code", code.name(), "message", message));
    else {
      err.println(code + ": " + message);
      err.flush();
    }
  }

  private void printJson(Object value) {
    try {
      out.println(MAPPER.writeValueAsString(value));
      out.flush();
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode CLI response");
    }
  }
}
