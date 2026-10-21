package dev.ryanbuu.infrapilot.api;

import dev.ryanbuu.infrapilot.audit.AuditService;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import dev.ryanbuu.infrapilot.dns.DnsModels.*;
import dev.ryanbuu.infrapilot.dns.DnsService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final DnsService dns;
  private final AuditService audit;
  private final InfraPilotProperties properties;

  public ApiController(DnsService dns, AuditService audit, InfraPilotProperties properties) {
    this.dns = dns;
    this.audit = audit;
    this.properties = properties;
  }

  @GetMapping("/meta")
  public Map<String, Object> meta() {
    return Map.of(
        "name", "InfraPilot", "version", "0.1.0", "demo", properties.getMode().equals("demo"));
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/session")
  public Map<String, Object> session(Authentication auth) {
    return Map.of(
        "username",
        auth.getName(),
        "admin",
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
  }

  @GetMapping("/zones")
  public List<ZoneView> zones() {
    return dns.zones();
  }

  @GetMapping("/zones/{id}/records")
  public ZoneRecords records(@PathVariable String id) {
    return dns.records(id);
  }

  @PostMapping("/zones/{id}/records")
  public Map<String, Long> create(
      @PathVariable String id, @Valid @RequestBody Change c, Authentication a) {
    return Map.of("auditId", dns.create(id, c, a.getName()));
  }

  @PutMapping("/zones/{id}/records")
  public Map<String, Long> update(
      @PathVariable String id, @Valid @RequestBody Change c, Authentication a) {
    return Map.of("auditId", dns.update(id, c, a.getName()));
  }

  @PostMapping("/zones/{id}/records/delete")
  public Map<String, Long> delete(
      @PathVariable String id, @RequestBody Mutation m, Authentication a) {
    return Map.of("auditId", dns.delete(id, m, a.getName()));
  }

  @GetMapping("/zones/{id}/probe")
  public List<ProbeResult> probe(
      @PathVariable String id,
      @RequestParam String name,
      @RequestParam(defaultValue = "A") String type) {
    return dns.probe(id, name, type);
  }

  @GetMapping("/audit")
  public List<AuditService.Event> events(
      @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
    return audit.list(Math.max(0, offset), Math.max(1, Math.min(100, limit)));
  }
}
