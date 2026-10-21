package dev.ryanbuu.infrapilot.dns;

import static org.springframework.http.HttpStatus.*;

import dev.ryanbuu.infrapilot.api.ApiException;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties.*;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties.Zone;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

@Component
@ConditionalOnProperty(name = "infrapilot.mode", havingValue = "live")
public class LiveDnsGateway implements DnsGateway {
  private final InfraPilotProperties properties;

  public LiveDnsGateway(InfraPilotProperties p) {
    properties = p;
    for (Key k : p.getKeys().values()) {
      if (k.secret() == null || k.secret().isBlank() || k.name() == null)
        throw new IllegalStateException("Live mode requires nonempty TSIG credentials");
      key(k);
    }
  }

  private TSIG key(Key k) {
    return new TSIG(k.algorithm() == null ? "hmac-sha256" : k.algorithm(), k.name(), k.secret());
  }

  private TSIG key(Zone z) {
    return key(properties.getKeys().get(z.key()));
  }

  private SimpleResolver resolver(Endpoint e, TSIG key) throws Exception {
    SimpleResolver r = new SimpleResolver(e.host());
    r.setPort(e.port());
    r.setTCP(true);
    r.setTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    if (key != null) r.setTSIGKey(key);
    return r;
  }

  @Override
  public List<Record> read(Zone zone) {
    try {
      ZoneTransferIn xfr =
          ZoneTransferIn.newAXFR(
              Name.fromString(zone.name()),
              new InetSocketAddress(zone.primary().host(), zone.primary().port()),
              key(zone));
      xfr.setTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
      xfr.run();
      List<Record> records = xfr.getAXFR();
      if (records.size() > 100000) throw new ApiException(BAD_GATEWAY, "区域记录过多，超过当前管理上限");
      return records;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(BAD_GATEWAY, "区域读取失败，请检查 AXFR、TSIG、view 和服务器连通性");
    }
  }

  @Override
  public void change(Zone z, List<Record> snapshot, List<Record> before, List<Record> after) {
    try {
      Update update = new Update(Name.fromString(z.name()));
      Record soa = snapshot.stream().filter(r -> r.getType() == Type.SOA).findFirst().orElseThrow();
      // BIND inline signing exposes a signed SOA serial via AXFR, while
      // UPDATE prerequisites compare the unsigned database. Retain RRset CAS
      // for signed zones; the signed SOA cannot be used as a prerequisite.
      boolean signed =
          snapshot.stream().anyMatch(r -> r.getType() == Type.RRSIG || r.getType() == Type.DNSKEY);
      if (!signed) update.present(soa.getName(), soa.getType(), soa.rdataToString());
      Record reference = before.isEmpty() ? after.getFirst() : before.getFirst();
      if (before.isEmpty()) update.absent(reference.getName(), reference.getType());
      else
        for (Record old : before) update.present(old.getName(), old.getType(), old.rdataToString());
      update.delete(reference.getName(), reference.getType());
      if (!after.isEmpty()) update.add(after.toArray(Record[]::new));
      Message response = resolver(z.primary(), key(z)).send(update);
      if (!response.isVerified())
        throw new UncertainDnsException("TSIG 响应校验失败，更新结果未确认；请先核对实际 DNS 记录");
      int r = response.getRcode();
      if (Set.of(Rcode.NXRRSET, Rcode.YXRRSET, Rcode.NXDOMAIN, Rcode.YXDOMAIN).contains(r))
        throw new ApiException(CONFLICT, "区域已被其他操作更新，请刷新后重试");
      if (r != Rcode.NOERROR)
        throw new ApiException(BAD_GATEWAY, "BIND 拒绝更新（" + Rcode.string(r) + "），请检查更新权限和记录约束");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new UncertainDnsException("DNS 更新结果未确认，请先刷新记录核实，勿重复提交");
    }
  }

  @Override
  public DnsModels.ProbeResult probe(
      Zone z, Endpoint e, String name, String type, boolean primary) {
    long started = System.nanoTime();
    try {
      SimpleResolver r = resolver(e, primary ? key(z) : null);
      Message answer =
          r.send(
              Message.newQuery(
                  Record.newRecord(Name.fromString(name), Type.value(type), DClass.IN)));
      Message soa =
          r.send(
              Message.newQuery(Record.newRecord(Name.fromString(z.name()), Type.SOA, DClass.IN)));
      if (primary && (!answer.isVerified() || !soa.isVerified()))
        throw new IllegalStateException("Unverified TSIG response");
      Long serial =
          soa.getSection(Section.ANSWER).stream()
              .filter(SOARecord.class::isInstance)
              .map(SOARecord.class::cast)
              .map(SOARecord::getSerial)
              .findFirst()
              .orElse(null);
      return new DnsModels.ProbeResult(
          e.label(),
          true,
          Rcode.string(answer.getRcode()),
          serial,
          answer.getSection(Section.ANSWER).stream().map(Record::toString).toList(),
          (System.nanoTime() - started) / 1000000,
          null);
    } catch (Exception ex) {
      return new DnsModels.ProbeResult(
          e.label(),
          false,
          null,
          null,
          List.of(),
          (System.nanoTime() - started) / 1000000,
          "查询失败，请检查服务器连通性");
    }
  }
}
