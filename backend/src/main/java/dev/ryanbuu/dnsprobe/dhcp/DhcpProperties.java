package dev.ryanbuu.dnsprobe.dhcp;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "probe.dhcp")
public class DhcpProperties {
  private boolean enabled = false;
  private List<Node> nodes = new ArrayList<>();

  public record Node(String id, String label, String url, String username, String password) {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> v) {
    nodes = v;
  }
}
