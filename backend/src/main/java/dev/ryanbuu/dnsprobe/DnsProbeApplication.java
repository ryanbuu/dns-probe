package dev.ryanbuu.dnsprobe;

import dev.ryanbuu.dnsprobe.config.ProbeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProbeProperties.class)
public class DnsProbeApplication {
  public static void main(String[] args) {
    SpringApplication.run(DnsProbeApplication.class, args);
  }
}
