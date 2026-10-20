package dev.ryanbuu.dnsprobe;

import static org.assertj.core.api.Assertions.*;

import dev.ryanbuu.dnsprobe.api.ApiException;
import dev.ryanbuu.dnsprobe.config.ProbeProperties;
import dev.ryanbuu.dnsprobe.config.ProbeProperties.*;
import dev.ryanbuu.dnsprobe.config.ProbeProperties.Zone;
import dev.ryanbuu.dnsprobe.dns.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

class LiveDnsGatewayTest {
  static final String SECRET =
      Base64.getEncoder().encodeToString("test-key-only-not-a-production-key".getBytes());

  Record record(String name, int type, String data) throws Exception {
    return Record.fromString(Name.fromString(name), type, DClass.IN, 300, data, Name.root);
  }

  @Test
  void axfrAndUpdateUseAuthenticatedDnsAndCasPrerequisites() throws Exception {
    var soa =
        record(
            "example.com.",
            Type.SOA,
            "ns.example.com. hostmaster.example.com. 123 3600 900 604800 300");
    var before = record("www.example.com.", Type.A, "192.0.2.1");
    var after = record("www.example.com.", Type.A, "192.0.2.2");
    try (DnsServer server = new DnsServer(List.of(soa, before, soa), Rcode.NOERROR)) {
      var pair = client(server);
      var gateway = pair.gateway();
      var z = pair.zone();
      var records = gateway.read(z);
      assertThat(records).contains(before);
      gateway.change(z, records, List.of(before), List.of(after));
      Message request = server.updates.poll(3, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.getSection(Section.PREREQ))
          .anyMatch(
              r -> r.getType() == Type.SOA && r.rdataToString().startsWith("ns.example.com."));
      assertThat(request.getSection(Section.PREREQ))
          .anyMatch(r -> r.getType() == Type.A && r.rdataToString().equals("192.0.2.1"));
      assertThat(request.getSection(Section.UPDATE))
          .anyMatch(r -> r.getDClass() == DClass.ANY && r.getType() == Type.A);
      assertThat(request.getSection(Section.UPDATE))
          .anyMatch(r -> r.getDClass() == DClass.IN && r.rdataToString().equals("192.0.2.2"));
      assertThat(server.failure).isNull();
    }
  }

  @Test
  void staleSoaIsReportedAsConflict() throws Exception {
    var soa =
        record(
            "example.com.",
            Type.SOA,
            "ns.example.com. hostmaster.example.com. 123 3600 900 604800 300");
    var a = record("www.example.com.", Type.A, "192.0.2.1");
    try (DnsServer server = new DnsServer(List.of(soa, a, soa), Rcode.NXRRSET)) {
      var p = client(server);
      assertThatThrownBy(() -> p.gateway.change(p.zone, List.of(soa), List.of(), List.of(a)))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    }
  }

  @Test
  void anUnsignedUpdateResponseIsNeverAcceptedAsSuccess() throws Exception {
    var soa =
        record(
            "example.com.",
            Type.SOA,
            "ns.example.com. hostmaster.example.com. 123 3600 900 604800 300");
    var a = record("www.example.com.", Type.A, "192.0.2.1");
    try (DnsServer server = new DnsServer(List.of(soa, a, soa), Rcode.NOERROR, false)) {
      var p = client(server);
      assertThatThrownBy(() -> p.gateway.change(p.zone, List.of(soa), List.of(), List.of(a)))
          .isInstanceOf(UncertainDnsException.class);
    }
  }

  record Client(LiveDnsGateway gateway, Zone zone) {}

  Client client(DnsServer s) {
    var p = new ProbeProperties();
    p.setMode("live");
    p.setKeys(Map.of("test", new Key("web-test.", "hmac-sha256", SECRET)));
    var z =
        new Zone(
            "test",
            "example.com.",
            "",
            false,
            "test",
            new Endpoint("primary", "127.0.0.1", s.socket.getLocalPort()),
            List.of(),
            List.of());
    return new Client(new LiveDnsGateway(p), z);
  }

  static class DnsServer implements AutoCloseable {
    final ServerSocket socket;
    final TSIG key = new TSIG("hmac-sha256", "web-test.", SECRET);
    final BlockingQueue<Message> updates = new LinkedBlockingQueue<>();
    volatile Throwable failure;
    volatile boolean closed;

    DnsServer(List<Record> records, int updateRcode) throws Exception {
      this(records, updateRcode, true);
    }

    DnsServer(List<Record> records, int updateRcode, boolean signResponse) throws Exception {
      socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
      Thread.startVirtualThread(
          () -> {
            while (!closed)
              try (Socket client = socket.accept()) {
                var in = new DataInputStream(client.getInputStream());
                byte[] wire = in.readNBytes(in.readUnsignedShort());
                Message request = new Message(wire);
                if (key.verify(request, wire, null) != Rcode.NOERROR)
                  throw new AssertionError("TSIG verification failed");
                Message response = new Message(request.getHeader().getID());
                response.getHeader().setFlag(Flags.QR);
                response.getHeader().setFlag(Flags.AA);
                response.getHeader().setOpcode(request.getHeader().getOpcode());
                response.addRecord(request.getQuestion(), Section.QUESTION);
                if (request.getHeader().getOpcode() == Opcode.UPDATE) {
                  updates.add(request);
                  response.getHeader().setRcode(updateRcode);
                } else for (Record r : records) response.addRecord(r, Section.ANSWER);
                if (signResponse) key.apply(response, request.getTSIG());
                byte[] answer = response.toWire();
                var out = new DataOutputStream(client.getOutputStream());
                out.writeShort(answer.length);
                out.write(answer);
                out.flush();
              } catch (Exception e) {
                if (!closed) failure = e;
              } catch (AssertionError e) {
                failure = e;
              }
          });
    }

    @Override
    public void close() throws Exception {
      closed = true;
      socket.close();
    }
  }
}
