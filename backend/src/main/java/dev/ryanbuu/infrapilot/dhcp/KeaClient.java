package dev.ryanbuu.infrapilot.dhcp;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.ryanbuu.infrapilot.api.ApiException;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class KeaClient {
  private final DhcpProperties p;
  private final InfraPilotProperties settings;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final Map<String, ObjectNode> demoConfigs = new LinkedHashMap<>();
  private final Map<String, ArrayNode> demoLeases = new LinkedHashMap<>();

  public KeaClient(DhcpProperties p, InfraPilotProperties settings, ObjectMapper mapper) {
    this.p = p;
    this.settings = settings;
    this.mapper = mapper;
  }

  public boolean enabled() {
    return settings.getMode().equals("demo") || p.isEnabled();
  }

  public List<DhcpProperties.Node> nodes() {
    return settings.getMode().equals("demo")
        ? List.of(
            new DhcpProperties.Node("kea1", "DHCP 主节点", "", "", ""),
            new DhcpProperties.Node("kea2", "DHCP 备节点", "", "", ""))
        : p.getNodes();
  }

  public JsonNode call(String id, String command, JsonNode args) {
    if (settings.getMode().equals("demo")) return demo(id, command, args);
    var n =
        nodes().stream()
            .filter(x -> x.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DHCP 服务节点不存在"));
    try {
      URI uri = URI.create(n.url());
      if (!Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null) throw new IllegalStateException();
      ObjectNode body = mapper.createObjectNode().put("command", command);
      body.putArray("service").add("dhcp4");
      if (args != null) body.set("arguments", args);
      String auth =
          Base64.getEncoder()
              .encodeToString((n.username() + ":" + n.password()).getBytes(StandardCharsets.UTF_8));
      var req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(8))
              .header("Content-Type", "application/json")
              .header("Authorization", "Basic " + auth)
              .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
              .build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || response.body().length() > 16_000_000)
        throw new IllegalStateException();
      JsonNode result = mapper.readTree(response.body());
      if (result.isArray()) result = result.path(0);
      int code = result.path("result").asInt(-1);
      if (code != 0 && !(code == 3 && Set.of("lease4-get-page", "lease4-del").contains(command)))
        throw new ApiException(
            HttpStatus.BAD_GATEWAY, n.label() + " 拒绝 " + command + "，请检查 DHCP 服务配置或命令权限");
      return result;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, n.label() + " 的 " + command + " 响应未确认，请检查节点状态后重试");
    }
  }

  private synchronized JsonNode demo(String id, String cmd, JsonNode args) {
    if (demoConfigs.isEmpty())
      try {
        for (String node : List.of("kea1", "kea2")) {
          ObjectNode cfg =
              (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/demo-dhcp.json"));
          demoConfigs.put(node, cfg);
          ArrayNode leases = mapper.createArrayNode();
          for (int i = 0; i < 4; i++) {
            ObjectNode l =
                leases
                    .addObject()
                    .put("ip-address", "198.51.100." + (101 + i))
                    .put("hw-address", "02:00:00:00:00:0" + (i + 1))
                    .put("subnet-id", 1)
                    .put("hostname", "device-" + (i + 1) + ".internal.dhcp.intra.example.com.")
                    .put("valid-lft", 7200)
                    .put("cltt", Instant.now().getEpochSecond() - 300)
                    .put("state", 0)
                    .put("fqdn-fwd", true)
                    .put("fqdn-rev", true);
            l.put("client-id", "01:02:00:00:00:00:0" + (i + 1));
          }
          demoLeases.put(node, leases);
        }
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    ObjectNode out = mapper.createObjectNode().put("result", 0);
    ObjectNode data = out.putObject("arguments");
    switch (cmd) {
      case "config-get" -> out.set("arguments", demoConfigs.get(id).deepCopy());
      case "config-test", "config-write" -> {}
      case "config-set" -> demoConfigs.put(id, args.deepCopy());
      case "version-get" -> out.put("text", "3.0.4");
      case "status-get" -> {
        ObjectNode ha =
            data.putArray("high-availability").addObject().put("ha-mode", "hot-standby");
        ObjectNode servers = ha.putObject("ha-servers");
        servers
            .putObject("local")
            .put("role", id.equals("kea1") ? "primary" : "standby")
            .put("state", "hot-standby");
        servers
            .putObject("remote")
            .put("in-touch", true)
            .put("communication-interrupted", false)
            .put("last-state", "hot-standby");
      }
      case "lease4-get-page" -> {
        ArrayNode ls = data.putArray("leases");
        String from = args.path("from").asText("start");
        if (from.equals("start")) ls.addAll(demoLeases.get(id));
      }
      case "lease4-del" -> {
        var ls = demoLeases.get(id);
        for (int i = ls.size() - 1; i >= 0; i--)
          if (ls.get(i).path("ip-address").asText().equals(args.path("ip-address").asText()))
            ls.remove(i);
      }
      case "lease4-resend-ddns" -> {}
      default -> throw new IllegalArgumentException("Unsupported demo command");
    }
    return out;
  }
}
