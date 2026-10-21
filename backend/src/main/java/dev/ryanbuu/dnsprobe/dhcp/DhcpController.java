package dev.ryanbuu.dnsprobe.dhcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dhcp")
public class DhcpController {
  private final DhcpService service;

  public DhcpController(DhcpService s) {
    service = s;
  }

  @GetMapping
  public ObjectNode get() {
    return service.view();
  }

  @PostMapping("/preview/{operation}")
  public Map<String, Long> preview(
      @PathVariable String operation, @RequestBody DhcpService.Change change, Authentication a) {
    return service.change(operation, change, a.getName(), true);
  }

  @PostMapping("/changes/{operation}")
  public Map<String, Long> change(
      @PathVariable String operation, @RequestBody DhcpService.Change change, Authentication a) {
    return service.change(operation, change, a.getName(), false);
  }

  @PostMapping("/leases")
  public Map<String, Long> lease(@RequestBody DhcpService.LeaseAction change, Authentication a) {
    return service.lease(change, a.getName());
  }
}
