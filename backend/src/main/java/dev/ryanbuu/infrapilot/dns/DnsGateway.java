package dev.ryanbuu.infrapilot.dns;

import dev.ryanbuu.infrapilot.config.InfraPilotProperties.Endpoint;
import dev.ryanbuu.infrapilot.config.InfraPilotProperties.Zone;
import java.util.List;
import org.xbill.DNS.Record;

public interface DnsGateway {
  List<Record> read(Zone zone);

  void change(Zone zone, List<Record> snapshot, List<Record> before, List<Record> after);

  DnsModels.ProbeResult probe(
      Zone zone, Endpoint endpoint, String name, String type, boolean primary);
}
