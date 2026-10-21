package dev.ryanbuu.infrapilot;

import dev.ryanbuu.infrapilot.config.InfraPilotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InfraPilotProperties.class)
public class InfraPilotApplication {
  public static void main(String[] args) {
    SpringApplication.run(InfraPilotApplication.class, args);
  }
}
