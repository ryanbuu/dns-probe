package dev.ryanbuu.infrapilot.config;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrapilot")
public class InfraPilotProperties {
  private String mode = "demo",
      adminUsername = "admin",
      adminPassword = "",
      viewerUsername = "viewer",
      viewerPassword = "";
  private int timeoutSeconds = 5;
  private List<Zone> zones = new ArrayList<>();
  private Map<String, Key> keys = new HashMap<>();

  public record Endpoint(String label, String host, int port) {
    public int port() {
      return port == 0 ? 53 : port;
    }
  }

  public record Key(String name, String algorithm, String secret) {}

  public record Zone(
      String id,
      String name,
      String description,
      Boolean readOnly,
      String key,
      Endpoint primary,
      List<Endpoint> secondaries,
      List<String> protectedSuffixes) {
    public boolean writable() {
      return Boolean.FALSE.equals(readOnly);
    }

    public List<Endpoint> replicas() {
      return secondaries == null ? List.of() : secondaries;
    }

    public List<String> protectedNames() {
      return protectedSuffixes == null ? List.of() : protectedSuffixes;
    }
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String v) {
    mode = v;
  }

  public String getAdminUsername() {
    return adminUsername;
  }

  public void setAdminUsername(String v) {
    adminUsername = v;
  }

  public String getAdminPassword() {
    return adminPassword;
  }

  public void setAdminPassword(String v) {
    adminPassword = v;
  }

  public String getViewerUsername() {
    return viewerUsername;
  }

  public void setViewerUsername(String v) {
    viewerUsername = v;
  }

  public String getViewerPassword() {
    return viewerPassword;
  }

  public void setViewerPassword(String v) {
    viewerPassword = v;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int v) {
    timeoutSeconds = v;
  }

  public List<Zone> getZones() {
    return zones;
  }

  public void setZones(List<Zone> v) {
    zones = v;
  }

  public Map<String, Key> getKeys() {
    return keys;
  }

  public void setKeys(Map<String, Key> v) {
    keys = v;
  }
}
