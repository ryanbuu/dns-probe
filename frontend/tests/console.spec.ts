import { test, expect } from "@playwright/test";
async function signIn(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "填入演示账户" }).click();
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(
    page.getByRole("heading", { name: "域名有序，服务可达。" }),
  ).toBeVisible();
}
test("record lifecycle, protected zones, comparison and audit", async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole("button", { name: /解析记录/ }).click();
  await expect(
    page.getByRole("heading", { name: "intra.example.com" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "添加记录" }).click();
  await page
    .getByRole("textbox", { name: "记录名称", exact: true })
    .fill("browser-test");
  await page
    .getByRole("textbox", { name: "记录值", exact: true })
    .fill("192.0.2.90");
  await page.getByRole("button", { name: "预览变更" }).click();
  await expect(page.getByText("确认这次变更")).toBeVisible();
  await page.getByRole("button", { name: "确认保存" }).click();
  const row = page
    .getByRole("row")
    .filter({ hasText: "browser-test.intra.example.com." });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "编辑记录" }).click();
  await page
    .getByRole("textbox", { name: "记录值", exact: true })
    .fill("192.0.2.91");
  await page.getByRole("button", { name: "预览变更" }).click();
  await page.getByRole("button", { name: "确认保存" }).click();
  await expect(row).toContainText("192.0.2.91");
  await page.getByRole("button", { name: "主从对比" }).click();
  await expect(
    page.locator(".probe-result strong").filter({ hasText: "bind1" }),
  ).toBeVisible();
  await expect(
    page.locator(".probe-result strong").filter({ hasText: "bind2" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await row.getByRole("button", { name: "删除记录" }).click();
  await page.getByRole("button", { name: "确认删除" }).click();
  await expect(row).toHaveCount(0);
  await page
    .getByRole("button", { name: /Kea 自动维护|dhcp.intra.example.com/ })
    .first()
    .click();
  await expect(page.getByRole("button", { name: "添加记录" })).toBeDisabled();
  await expect(
    page.getByText("此区域由 Kea 自动维护", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: /操作审计/ }).click();
  await expect(
    page
      .getByRole("row")
      .filter({ hasText: "browser-test.intra.example.com." }),
  ).toHaveCount(3);
  await page.getByRole("button", { name: "退出登录" }).click();
  await expect(page.getByRole("heading", { name: "欢迎回来" })).toBeVisible();
});
test("desktop and mobile views remain usable", async ({ page }) => {
  await signIn(page);
  await page.screenshot({
    path: "../docs/screenshots/overview.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: /解析记录/ }).click();
  await expect(
    page.getByRole("heading", { name: "intra.example.com" }),
  ).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/records.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("button", { name: "打开菜单" })).toBeVisible();
  await page.getByRole("button", { name: "打开菜单" }).click();
  await page.getByRole("button", { name: /概览/ }).click();
  await expect(
    page.getByRole("heading", { name: "域名有序，服务可达。" }),
  ).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  const dimensions = await page.evaluate(() => ({
    body: document.documentElement.scrollWidth,
    viewport: innerWidth,
  }));
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport);
});
