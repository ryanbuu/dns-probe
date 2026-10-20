package dev.ryanbuu.dnsprobe.dns;

import dev.ryanbuu.dnsprobe.api.ApiException;
import org.springframework.http.HttpStatus;

/** The server may have applied the update; do not automatically retry. */
public class UncertainDnsException extends ApiException {
  public UncertainDnsException(String message) {
    super(HttpStatus.BAD_GATEWAY, message);
  }
}
