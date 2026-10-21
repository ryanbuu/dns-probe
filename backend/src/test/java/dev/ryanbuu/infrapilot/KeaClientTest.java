package dev.ryanbuu.infrapilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.ryanbuu.infrapilot.api.ApiException;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import dev.ryanbuu.infrapilot.dhcp.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class KeaClientTest {
  @Test
  void speaksAuthenticatedControlAgentProtocolAndDoesNotFollowRedirects() throws Exception {
    var mapper = new ObjectMapper();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var requests = new ArrayList<String>();
    server.createContext(
        "/",
        exchange -> {
          try {
            requests.add(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.path("service").get(0).asText()).isEqualTo("dhcp4");
            assertThat(body.path("command").asText()).isEqualTo("version-get");
            byte[] result = "[{\"result\":0,\"text\":\"3.0.4\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
          } finally {
            exchange.close();
          }
        });
    server.createContext(
        "/redirect",
        exchange -> {
          exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.start();
    try {
      var p = new DhcpProperties();
      p.setEnabled(true);
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      p.setNodes(
          List.of(new DhcpProperties.Node("test", "Test Kea", base, "infrapilot", "sample-test-only")));
      var settings = new InfraPilotProperties();
      settings.setMode("live");
      var c = new KeaClient(p, settings, mapper);
      assertThat(c.call("test", "version-get", null).path("text").asText()).isEqualTo("3.0.4");
      assertThat(requests)
          .containsExactly(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString("infrapilot:sample-test-only".getBytes(StandardCharsets.UTF_8)));
      p.setNodes(
          List.of(
              new DhcpProperties.Node(
                  "test", "Test Kea", base + "/redirect", "infrapilot", "sample-test-only")));
      assertThatThrownBy(() -> c.call("test", "version-get", null))
          .isInstanceOf(ApiException.class)
          .hasMessageNotContaining("sample-test-only");
    } finally {
      server.stop(0);
    }
  }
}
