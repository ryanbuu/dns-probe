package dev.ryanbuu.infrapilot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.ryanbuu.infrapilot.api.ApiException;
import dev.ryanbuu.infrapilot.audit.AuditService;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import dev.ryanbuu.infrapilot.dhcp.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DhcpServiceTest {
  final ObjectMapper mapper = new ObjectMapper();
  final AuditService audit = mock(AuditService.class);
  final InfraPilotProperties settings = new InfraPilotProperties();

  class Client extends KeaClient {
    String failNode = "";
    boolean failed;
    final List<String> commands = new ArrayList<>();

    Client() {
      super(new DhcpProperties(), settings, mapper);
    }

    public JsonNode call(String node, String cmd, JsonNode args) {
      commands.add(node + ":" + cmd);
      if (node.equals(failNode) && cmd.equals("config-set") && !failed) {
        failed = true;
        throw new ApiException(HttpStatus.BAD_GATEWAY, "test failure");
      }
      return super.call(node, cmd, args);
    }
  }

  final Client client = new Client();
  final DhcpService service = new DhcpService(client, mapper, audit);

  ObjectNode reservation(String mac, String ip) {
    return mapper
        .createObjectNode()
        .put("mac", mac)
        .put("address", ip)
        .put("hostname", "test-device");
  }

  DhcpService.Change change(String revision, int id, JsonNode values) {
    return new DhcpService.Change(revision, id, values);
  }

  @Test
  void readsMergedLeasesAndNeverExposesConfigurationCredentials() {
    var v = service.view();
    assertThat(v.path("nodes").size()).isEqualTo(2);
    assertThat(v.path("leases").size()).isEqualTo(4);
    assertThat(v.path("leases").get(0).path("seenOn").size()).isEqualTo(2);
    assertThat(v.path("writable").asBoolean()).isTrue();
    assertThat(v.toString()).doesNotContain("password", "Authorization", "hooks-libraries");
  }

  @Test
  void previewDoesNotMutateAndReservationChangesPersistToBothNodes() {
    var v = service.view();
    var c =
        change(v.path("revision").asText(), 1, reservation("02:00:00:00:00:ee", "198.51.100.60"));
    service.change("reservation-save", c, "admin", true);
    assertThat(service.view().path("subnets").get(0).path("reservations").size()).isEqualTo(1);
    assertThat(client.commands).doesNotContain("kea1:config-set", "kea2:config-set");
    service.change("reservation-save", c, "admin", false);
    var after = service.view();
    assertThat(after.path("aligned").asBoolean()).isTrue();
    assertThat(after.path("subnets").get(0).path("reservations").size()).isEqualTo(2);
    assertThat(client.commands)
        .containsSubsequence(
            "kea2:config-set", "kea2:config-write", "kea1:config-set", "kea1:config-write");
    verify(audit).finish(0, "SUCCESS", "主备节点已校验、更新并持久化");
  }

  @Test
  void staleRevisionAndAddressInUseAreRejectedBeforeWrites() {
    var rev = service.view().path("revision").asText();
    assertThatThrownBy(
            () ->
                service.change(
                    "reservation-save",
                    change("old", 1, reservation("02:00:00:00:00:ee", "198.51.100.60")),
                    "a",
                    false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    assertThatThrownBy(
            () ->
                service.change(
                    "reservation-save",
                    change(rev, 1, reservation("02:00:00:00:00:ee", "198.51.100.101")),
                    "a",
                    false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    assertThat(client.commands).doesNotContain("kea1:config-set", "kea2:config-set");
  }

  @Test
  void secondNodeFailureRollsBackFirstNodeAndRecordsFailure() {
    var before = service.view();
    client.failNode = "kea1";
    assertThatThrownBy(
            () ->
                service.change(
                    "reservation-save",
                    change(
                        before.path("revision").asText(),
                        1,
                        reservation("02:00:00:00:00:ee", "198.51.100.60")),
                    "a",
                    false))
        .isInstanceOf(ApiException.class);
    assertThat(service.view().path("revision")).isEqualTo(before.path("revision"));
    verify(audit).finish(0, "FAILED", "配置同步失败，已恢复之前配置");
  }

  @Test
  void auditFailureBlocksAllConfigurationWrites() {
    doThrow(new IllegalStateException("database unavailable"))
        .when(audit)
        .beginObject(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    var c =
        change(
            service.view().path("revision").asText(),
            1,
            reservation("02:00:00:00:00:ee", "198.51.100.60"));
    assertThatThrownBy(() -> service.change("reservation-save", c, "admin", false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(client.commands).doesNotContain("kea1:config-set", "kea2:config-set");
  }

  @Test
  void subnetEditsRetainUnknownSettingsAndRejectOverlappingOrActiveLeaseRemoval() {
    var state = service.view();
    ObjectNode values = (ObjectNode) state.path("subnets").get(0).deepCopy();
    values.put("validLifetime", 3600);
    service.change("subnet-save", change(state.path("revision").asText(), 1, values), "a", false);
    assertThat(service.view().path("subnets").get(0).path("validLifetime").asInt()).isEqualTo(3600);
    values.putArray("pools").add("198.51.100.200 - 198.51.100.254");
    assertThatThrownBy(
            () ->
                service.change(
                    "subnet-save",
                    change(service.view().path("revision").asText(), 1, values),
                    "a",
                    true))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.change(
                    "subnet-delete",
                    change(service.view().path("revision").asText(), 1, mapper.createObjectNode()),
                    "a",
                    false))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void updatesPreserveEachNodesOwnHaSettingsAndUnknownSubnetOptions() {
    for (String node : List.of("kea1", "kea2")) {
      ObjectNode cfg =
          (ObjectNode) client.call(node, "config-get", null).path("arguments").deepCopy();
      ((ObjectNode) cfg.path("Dhcp4")).put("server-tag", node);
      ((ObjectNode) cfg.path("Dhcp4").path("subnet4").get(0))
          .putObject("user-context")
          .put("retained", true);
      client.call(node, "config-set", cfg);
    }
    var state = service.view();
    assertThat(state.path("aligned").asBoolean()).isTrue();
    service.change(
        "reservation-save",
        change(
            state.path("revision").asText(), 1, reservation("02:00:00:00:00:ee", "198.51.100.60")),
        "admin",
        false);
    for (String node : List.of("kea1", "kea2")) {
      var cfg = client.call(node, "config-get", null).path("arguments").path("Dhcp4");
      assertThat(cfg.path("server-tag").asText()).isEqualTo(node);
      assertThat(cfg.path("subnet4").get(0).path("user-context").path("retained").asBoolean())
          .isTrue();
    }
  }

  @Test
  void leaseReleaseAffectsBothCopiesAndRequestsDdnsCleanupOnce() {
    var l = service.view().path("leases").get(0);
    service.lease(
        new DhcpService.LeaseAction(
            l.path("revision").asText(), l.path("address").asText(), "release"),
        "admin");
    assertThat(service.view().path("leases").size()).isEqualTo(3);
    assertThat(client.commands).contains("kea1:lease4-del", "kea2:lease4-del");
    verify(audit).finish(0, "SUCCESS", "两台租约已释放，已请求清理 DDNS");
  }
}
