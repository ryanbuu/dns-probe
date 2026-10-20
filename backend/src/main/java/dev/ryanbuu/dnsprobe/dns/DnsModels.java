package dev.ryanbuu.dnsprobe.dns;

import jakarta.validation.constraints.*;
import java.util.List;

public final class DnsModels {
  private DnsModels() {}

  public record Rrset(
      String name,
      String type,
      long ttl,
      List<String> values,
      String revision,
      boolean editable,
      String protection) {}

  public record Snapshot(long serial, List<Rrset> records) {}

  public record ZoneView(
      String id,
      String name,
      String description,
      boolean readOnly,
      String primary,
      List<String> secondaries,
      Long serial,
      Integer recordCount,
      String error) {}

  public record ZoneRecords(ZoneView zone, List<Rrset> records) {}

  public record Change(
      @NotBlank @Size(max = 255) String name,
      @NotBlank String type,
      @Min(0) @Max(2147483647) long ttl,
      @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 4096) String> values,
      String expectedRevision) {}

  public record Mutation(String name, String type, String expectedRevision) {}

  public record ProbeResult(
      String server,
      boolean reachable,
      String rcode,
      Long serial,
      List<String> answers,
      long elapsedMs,
      String error) {}
}
