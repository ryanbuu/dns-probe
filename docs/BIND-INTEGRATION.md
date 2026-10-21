# 接入现有 BIND 与 Kea

目标拓扑：bind1（192.0.2.10）拥有 `writers` 主区域；bind1 的 `readers` 与 bind2（192.0.2.11）提供副本。Kea1/2 使用自己的 TSIG 更新 DHCP 子域与客户端反向区域。DNS Probe 作为新的、独立的 DNS UPDATE 客户端加入，不覆盖现有配置。

以下是人工合并示例，**不要整段覆盖已有 named.conf 或 update-policy**。仓库实现尚未自动部署这些权限。

## 1. 独立身份

在安全的管理环境生成：

```sh
tsig-keygen -a hmac-sha256 dns-probe.intra.example.com.
```

将生成的 key 块保存在 bind1 的单独受保护配置文件，使用 `include` 加载。把相同密钥名称、算法及 Base64 secret 配置给面板。密钥文件只授予 root / bind 读取；面板使用环境变量传递 secret，不需要获取现有 Kea 密钥、签名私钥或 RNDC 密钥。

在 bind1 的 `writers` view 中，保留现有 match-clients 成员，增加：

```bind
key "dns-probe.intra.example.com.";
```

该 TSIG 身份必须匹配到 writers，而不能落入 readers。view 名称不是 DNS UPDATE 报文中的参数，BIND 使用 TSIG 身份选 view。

## 2. 区域读取

在五个 writers 主区域的 `allow-transfer` 里保留现有主从 TSIG 身份，再加入：

```bind
key "dns-probe.intra.example.com.";
```

来源网络限制可在防火墙收窄到 Docker 主机。无需开放匿名 AXFR，也无需让面板直接修改从区域。

## 3. 精确的写入权限

`intra.example.com` 原有静态主区域可增加：

```bind
update-policy {
    grant dns-probe.intra.example.com. zonesub A AAAA CNAME MX TXT SRV CAA;
};
```

`2.0.192.in-addr.arpa` 可增加：

```bind
update-policy {
    grant dns-probe.intra.example.com. zonesub PTR;
};
```

面板侧对 `dhcp.intra.example.com` 委派子域设置 protected-suffixes。若需要在 BIND 侧同样收窄主域权限，可用更细的 `name` / `wildcard` 规则替代上面的 zonesub；不要在 `intra` 中创建被委派 DHCP 子域的遮蔽记录。

**不要给面板密钥授权 DHCP 区域的更新权限**：

- `dhcp.intra.example.com`
- `100.51.198.in-addr.arpa`
- `113.0.203.in-addr.arpa`

这三处保留原有 Kea update-policy，面板只授权 AXFR。这样即使前端限制被绕过，面板身份也不能覆盖 Kea 的记录及 DHCID。

同一个 zone 块不能同时配置 `allow-update` 与 `update-policy`。启用动态更新后，不要再直接修改正在运行区域的文件；使用动态更新，必要时按 BIND 官方 freeze / thaw 流程维护。现有 inline-signing 和 dnssec-policy 保留，签名由 BIND 自动管理。

## 4. 更新原子性及同步

一次编辑针对同一 `(name,type)` 的完整记录集：

1. AXFR 重新读取最新内容，校验前端携带的记录版本。
2. 构造带 TSIG 的 RFC2136 UPDATE。
3. 对旧记录集设置 prerequisite，未签名区域额外对旧 SOA 设置 prerequisite；新增记录要求同名同类型不存在。
4. 在同一个 UPDATE 中替换或删除该记录集。
5. BIND 写入日志并更新 SOA，现有通知和 IXFR/AXFR 路径同步副本。

未签名区域的 SOA 前置条件可以发现区域级并发变化。BIND inline signing 的签名 SOA 与原始区 SOA 序列号可能不同，因此签名区域只使用记录值前置条件，并在提交前校验包含 TTL 的记录版本。RFC2136 的记录值前置条件不比较 TTL：签名区域在服务端读取后发生的并发 TTL 单独修改，无法在同一 UPDATE 中原子检测。不会依赖网页里的旧快照重写整个区域。

跨区域 A/PTR 不是单个 DNS 事务，当前版本需要分别维护。已有 DHCP 客户端的域名需要修改时，应从 Kea 的客户端命名或保留配置入手。

## 5. 验收与回退

先备份 BIND 配置，执行 `named-checkconf`，使用 `rndc reconfig` 应用权限，避免重启整个 DNS 服务。用临时专用记录验收：

- 五个区域可以读取，且不通过匿名 AXFR。
- 固定服务区域可以新增、修改、删除测试 A/PTR；记录和 SOA 在 bind1 readers / bind2 一致。
- DNSSEC 签名保持有效。
- 三个 DHCP 区域前端和后端均拒绝人工写入；BIND 也拒绝面板 TSIG 更新。
- Kea 实际 DORA / 续租后 A/PTR/DHCID 更新仍正常。
- 操作审计包含完整前后值与结果，测试数据已清理。

回退只需停止 DNS Probe，并从 BIND 中撤销其 key、match-clients、allow-transfer 和新增 update-policy 权限；保留原有 Kea 身份、主从密钥、区域文件及签名策略。回退面板不会自动撤销已确认的人工记录变更，需要按审计逐项处理。

官方参考：

- https://bind9.readthedocs.io/en/v9.20.29/reference.html
- https://github.com/dnsjava/dnsjava
