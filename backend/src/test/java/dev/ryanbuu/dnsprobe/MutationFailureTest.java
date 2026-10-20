package dev.ryanbuu.dnsprobe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

import dev.ryanbuu.dnsprobe.api.ApiException;
import dev.ryanbuu.dnsprobe.audit.AuditService;
import dev.ryanbuu.dnsprobe.config.ProbeProperties;
import dev.ryanbuu.dnsprobe.dns.*;
import dev.ryanbuu.dnsprobe.dns.DnsModels.Change;
import java.util.*;
import org.junit.jupiter.api.Test;

class MutationFailureTest {
  ProbeProperties properties = new ProbeProperties();
  ZoneRegistry registry = new ZoneRegistry(properties);
  DnsGateway gateway = spy(new DemoDnsGateway(registry));
  AuditService audit = mock(AuditService.class);
  DnsService service = new DnsService(registry, gateway, new RecordPolicy(), audit);
  Change change = new Change("failure-test", "A", 300, List.of("192.0.2.77"), null);

  @Test
  void dnsFailureIsAuditedAndIsNotReportedAsSuccess() {
    when(audit.begin(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(42L);
    doThrow(new ApiException(BAD_GATEWAY, "DNS 拒绝更新"))
        .when(gateway)
        .change(any(), anyList(), anyList(), anyList());
    assertThatThrownBy(() -> service.create("intra", change, "admin"))
        .isInstanceOf(ApiException.class);
    verify(audit).finish(42L, "FAILED", "DNS 拒绝更新");
    verify(audit, never()).finish(eq(42L), eq("SUCCESS"), anyString());
  }

  @Test
  void noDnsWriteOccursWhenAuditIntentCannotBeStored() {
    when(audit.begin(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenThrow(new IllegalStateException("storage unavailable"));
    assertThatThrownBy(() -> service.create("intra", change, "admin"))
        .isInstanceOf(IllegalStateException.class);
    verify(gateway, never()).change(any(), anyList(), anyList(), anyList());
  }

  @Test
  void incompleteAuditAfterSuccessfulDnsWriteReturnsAnExplicitUncertainResult() {
    when(audit.begin(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(42L);
    doThrow(new IllegalStateException("storage unavailable"))
        .when(audit)
        .finish(eq(42L), eq("SUCCESS"), anyString());
    assertThatThrownBy(() -> service.create("intra", change, "admin"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(SERVICE_UNAVAILABLE);
              assertThat(e.getMessage()).contains("DNS 已更新");
            });
    assertThat(service.records("intra").records())
        .anyMatch(r -> r.name().startsWith("failure-test."));
  }

  @Test
  void uncertainNetworkResultsRemainPendingForReconciliation() {
    when(audit.begin(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(42L);
    doThrow(new UncertainDnsException("结果未确认"))
        .when(gateway)
        .change(any(), anyList(), anyList(), anyList());
    assertThatThrownBy(() -> service.create("intra", change, "admin"))
        .isInstanceOf(UncertainDnsException.class);
    verify(audit).finish(42L, "PENDING", "结果未确认");
  }
}
