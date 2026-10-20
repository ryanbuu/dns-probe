package dev.ryanbuu.dnsprobe.dns;

import dev.ryanbuu.dnsprobe.config.ProbeProperties.Endpoint;
import dev.ryanbuu.dnsprobe.config.ProbeProperties.Zone;
import java.util.List;
import org.xbill.DNS.Record;

public interface DnsGateway {
  List<Record> read(Zone zone);

  void change(Zone zone, List<Record> snapshot, List<Record> before, List<Record> after);

  DnsModels.ProbeResult probe(
      Zone zone, Endpoint endpoint, String name, String type, boolean primary);
}
