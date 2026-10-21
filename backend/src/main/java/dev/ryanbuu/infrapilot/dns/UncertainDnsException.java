package dev.ryanbuu.infrapilot.dns;

import dev.ryanbuu.infrapilot.api.ApiException;
import org.springframework.http.HttpStatus;

/** The server may have applied the update; do not automatically retry. */
public class UncertainDnsException extends ApiException {
  public UncertainDnsException(String message) {
    super(HttpStatus.BAD_GATEWAY, message);
  }
}
