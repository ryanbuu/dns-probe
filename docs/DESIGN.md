# DNS 控制台布局参考

参考 Cloudflare 的 [2026 DNS 面板更新](https://developers.cloudflare.com/changelog/post/2026-05-20-new-dns-records-ux/) 与 [DNS 记录管理说明](https://developers.cloudflare.com/dns/manage-dns-records/how-to/create-dns-records/)。官方展示的方向包括更灵活的记录表格、长内容显示、手机端卡片，以及页面内 DNS / TTL 说明。

| Before | After | Why |
| --- | --- | --- |
| 区域侧栏与全局侧栏并排 | 页头区域选择器与通栏表格 | 给名称和长记录值留出空间 |
| 编辑弹窗 | 新增在表格前、编辑在记录下方展开 | 操作时仍能看到记录所在上下文 |
| 分散的记录工具 | 搜索、筛选、列显示、导出集中排列 | 缩短常用操作的查找路径 |
| 手机缩小桌面表格 | 独立的记录卡片与分页 | 小屏直接阅读完整值和操作记录 |
| 装饰性的卡片和文案 | 中性灰边框、常规字号和区域信息 | 让 DNS 记录成为页面主体 |

Ant Design Vue 继续采用默认组件主题。保留 DNS Probe 自有名称和标识，页面字段对应实际 BIND / Kea 功能。

可用功能：按名称或内容搜索、类型过滤、系统记录和可编辑记录过滤、内容/维护方式/TTL 列开关、当前筛选记录的 zone 文本片段导出、提交前变更预览。导出的文本片段包含当前筛选记录，不保证包含完整区域所需的 SOA/NS。

权限、TSIG、记录版本检查及审计均由既有后端执行，界面改版不修改生产 BIND 策略。
