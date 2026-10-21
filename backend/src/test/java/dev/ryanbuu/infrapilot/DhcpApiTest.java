package dev.ryanbuu.infrapilot;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:dhcp-api", "infrapilot.mode=demo"})
@AutoConfigureMockMvc
class DhcpApiTest {
  @Autowired MockMvc mvc;

  @Test
  void readsRequireAuthenticationAndWritesRequireAdminAndCsrf() throws Exception {
    mvc.perform(get("/api/dhcp")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/dhcp").with(user("viewer").roles("VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.leases.length()").value(4));
    String body = "{\"revision\":\"stale\",\"subnetId\":1,\"values\":{}}";
    mvc.perform(
            post("/api/dhcp/changes/subnet-save")
                .with(user("viewer").roles("VIEWER"))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/dhcp/changes/subnet-save")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/dhcp/changes/subnet-save")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }
}
