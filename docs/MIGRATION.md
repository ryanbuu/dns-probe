# InfraPilot 命名迁移

统一名称为 **InfraPilot**，机器标识为 `infrapilot`。

| 对象 | 新名称 |
| --- | --- |
| Git 仓库、部署目录 | `infrapilot`、`/opt/ryanbuu/infrapilot` |
| Java 包、启动类 | `dev.ryanbuu.infrapilot`、`InfraPilotApplication` |
| Maven / npm | `infrapilot` / `infrapilot-web` |
| Compose 项目及镜像 | `infrapilot`、`infrapilot-backend`、`infrapilot-frontend` |
| 容器 | `infrapilot-backend-1`、`infrapilot-frontend-1`、`infrapilot-postgres-1` |
| 数据库及应用账号 | `infrapilot` |
| 审计表 | `infrapilot_audit_event` |
| PostgreSQL 序列、主键 | `infrapilot_audit_event_id_seq`、`infrapilot_audit_event_pkey` |
| 配置、环境变量、会话 Cookie | `infrapilot.*`、`INFRAPILOT_*`、`INFRAPILOT_SESSION` |
| BIND / Kea 专用身份 | `infrapilot`，TSIG 名称以实际域配置 |

## 已有 PostgreSQL 数据

迁移前停止旧应用写入，导出数据库与部署配置。保留原数据库卷，在新 Compose 项目中创建独立卷、数据库和最小权限账号，再将备份以新账号作为所有者恢复。不要让新应用先初始化空表后再恢复旧审计。

对恢复的数据库运行 [迁移 SQL](../deploy/migrate-legacy-postgres.sql)，将旧 `audit_event` 表、身份序列和主键改名。脚本使用事务，发现新旧审计表同时存在时拒绝继续；审计内容和 ID 不重写。完成后核对行数、排序后的数据摘要、表所有者及序列，再启动应用。

新建 PostgreSQL 的示例覆盖文件为 [compose.postgres.example.yaml](../deploy/compose.postgres.example.yaml)：

```sh
docker compose -f compose.yaml -f deploy/compose.postgres.example.yaml up -d --build
```

同时将 `.env` 的数据库连接设为 `jdbc:postgresql://postgres:5432/infrapilot`，账号设为 `infrapilot`，并设置独立的应用密码和 `INFRAPILOT_POSTGRES_PASSWORD`。生产凭据不提交 Git。

## 服务身份和入口

BIND 先并行授权新 TSIG 名称，使用新身份验证 AXFR 和 RFC2136 权限后再撤销旧身份；保留现有 Kea DDNS、主从传送及 DNSSEC 配置。Kea Control Agent 使用新的应用账号，不更改 DHCP 配置或租约数据库。

建立新的 APISIX service/route 和域名，验证 HTTPS、登录、DNS、DHCP 与历史审计后切换入口。回滚时恢复旧路由、旧应用及原数据库卷；切换后产生的新审计应先备份核对，避免丢失。原备份中的历史标识保留，便于恢复和追溯。
