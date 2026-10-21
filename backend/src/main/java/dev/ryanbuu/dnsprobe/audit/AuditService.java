package dev.ryanbuu.dnsprobe.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ryanbuu.dnsprobe.dns.DnsModels.Rrset;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public long begin(
      String actor,
      String zone,
      String action,
      String name,
      String type,
      Rrset before,
      Rrset after) {
    Map<String, Object> args = new HashMap<>();
    args.put("occurred_at", java.sql.Timestamp.from(java.time.Instant.now()));
    args.put("actor", actor);
    args.put("zone_id", zone);
    args.put("action", action);
    args.put("record_name", name);
    args.put("record_type", type);
    args.put("before_json", json(before));
    args.put("after_json", json(after));
    args.put("status", "PENDING");
    args.put("detail", "");
    return new SimpleJdbcInsert(jdbc)
        .withTableName("audit_event")
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(args)
        .longValue();
  }

  public void finish(long id, String status, String detail) {
    jdbc.update("UPDATE audit_event SET status=?, detail=? WHERE id=?", status, detail, id);
  }

  public List<Event> list(int offset, int limit) {
    return jdbc.query(
        "SELECT * FROM audit_event ORDER BY id DESC LIMIT ? OFFSET ?",
        (r, i) ->
            new Event(
                r.getLong("id"),
                r.getObject("occurred_at", OffsetDateTime.class),
                r.getString("actor"),
                r.getString("zone_id"),
                r.getString("action"),
                r.getString("record_name"),
                r.getString("record_type"),
                r.getString("before_json"),
                r.getString("after_json"),
                r.getString("status"),
                r.getString("detail")),
        limit,
        offset);
  }

  private String json(Object v) {
    try {
      return mapper.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize audit event");
    }
  }

  public record Event(
      long id,
      OffsetDateTime occurredAt,
      String actor,
      String zoneId,
      String action,
      String recordName,
      String recordType,
      String beforeJson,
      String afterJson,
      String status,
      String detail) {}
}
