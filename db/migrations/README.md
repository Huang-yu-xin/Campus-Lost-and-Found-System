# 数据库迁移

本项目使用 **Flyway** 做版本化迁移（禁止手工改表部署）。

## 权威位置
实际由 Spring Boot 启动时执行的迁移脚本位于 Flyway 默认 classpath 位置：

```
server/src/main/resources/db/migration/V{n}__{desc}.sql
```

- `V1__baseline.sql` — P1 基线 schema 草案（覆盖任务书 §6 全部逻辑实体）。
- 后续演进一律**新增** `V2__...`、`V3__...`，不回改历史迁移。

## 本目录用途
`db/migrations/`（仓库根）保留用于：
- 存放大型/手动运维迁移说明；
- 若团队后续决定将迁移改为 `filesystem:` 读取，则迁移文件迁入此处，并同步修改 `application.yml` 的 `spring.flyway.locations`。

## 种子数据
非生产示例数据见 `db/seeds/`（拒绝生产默认弱口令）。
