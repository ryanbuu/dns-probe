import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:5173",
    headless: true,
    channel: process.env.CI ? undefined : "chrome",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command:
        'mvn -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.arguments="--server.port=18080 --spring.datasource.url=jdbc:h2:mem:browser-tests"',
      url: "http://127.0.0.1:18080/api/meta",
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
    {
      command: "npm run dev -- --port 5173",
      url: "http://127.0.0.1:5173",
      reuseExistingServer: !process.env.CI,
      env: { API_PROXY_TARGET: "http://127.0.0.1:18080" },
    },
  ],
});
