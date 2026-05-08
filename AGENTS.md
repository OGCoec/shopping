# Codex 项目实现规范

## 集合访问必须批量化

本项目中，只要业务传入的是集合、列表、批量 key、批量 id、批量任务，就必须优先使用批量访问方式。

禁止把集合拆成单个元素后，在 Java 层 `for` 循环里逐个访问 Redis、Caffeine、数据库。

目标：

- Redis：一次业务动作尽量一次 Redis 网络往返。
- Caffeine：使用本地批量 API，避免无意义的单 key 循环封装。
- DB：一次业务动作尽量一次 SQL / 一次 Mapper 调用。
- MQ、邮件、第三方接口等外部动作如果不支持批量，可以循环发送，但数据库状态变更必须优先批量完成。

## Redis 规范

处理多个 Redis key 时，优先使用：

- `multiGet(Collection<String>)`
- `multiSet(Map<String, String>)`
- `delete(Collection<String>)`
- `executePipelined(...)`
- Lua 脚本一次完成 `SCAN + DEL / SET / INCR / DECR`
- Lua 脚本一次完成集合判断、选择、扣减、写回

禁止：

```java
for (String key : keys) {
    stringRedisTemplate.delete(key);
}
```

应该改成：

```java
stringRedisTemplate.delete(keys);
```

如果业务要求“一次 Redis 网络延迟”，并且逻辑包含 `SCAN`、筛选、扣减、批量删除等多个 Redis 操作，应该把完整逻辑放到 Lua 脚本中，通过一次 `execute(...)` 完成。

## Caffeine 规范

Caffeine 是本地内存，没有 Redis 的网络 RTT 问题，但集合场景仍然优先使用批量 API：

- `cache.getAllPresent(keys)`
- `cache.putAll(map)`
- `cache.invalidateAll(keys)`

禁止把集合拆成多个单 key 查询方法调用。

禁止：

```java
for (String key : keys) {
    queryOneFromLocalCache(key);
}
```

应该优先改成：

```java
cache.getAllPresent(keys);
```

## DB / Mapper 规范

持久层处理集合时，Mapper 必须优先直接接收集合参数，一次 SQL 完成。

禁止：

```java
for (Long id : ids) {
    mapper.deleteById(id);
}
```

应该改成：

```java
mapper.deleteByIds(ids);
```

SQL 可使用：

```sql
DELETE FROM table_name
WHERE id IN (...)
```

PostgreSQL 场景也可以使用：

```sql
DELETE FROM table_name
WHERE id = ANY(...)
```

如果要“查出一批，再更新/删除一批”，优先使用一条 CTE SQL 完成，不要在 Java 层 `for` 循环逐条处理。

示例：

```sql
WITH target AS (
    SELECT id
    FROM table_name
    WHERE condition = true
)
DELETE FROM table_name t
USING target
WHERE t.id = target.id;
```

## 缓存查询链路规范

集合查询链路必须按批量方式处理：

```text
Caffeine getAllPresent(keys)
    -> Redis multiGet(missingKeys)
    -> DB batch query(redisMissingKeys)
    -> Redis multiSet / pipeline / Lua write back
    -> Caffeine putAll
```

禁止对集合里的每个 key 调用完整单体链路：

```java
for (String key : keys) {
    queryOne(key);
}
```

## 允许分批的例外

只有以下情况允许分批：

- 集合特别大，单次 Redis Lua / SQL 参数过多会阻塞或超过限制。
- 单次 Redis Lua 可能长时间阻塞 Redis 主线程。
- 单次 SQL 可能锁表或造成过大的事务。
- 第三方接口本身不支持批量。
- 业务明确要求逐条隔离失败。

即使允许分批，每一批内部也必须是批量访问，不允许逐条访问。

分批时必须在代码或提交说明里写清楚原因。

## Codex 修改代码前必须检查

修改本项目时必须检查：

- 是否存在 `for` 循环里调用 Redis。
- 是否存在 `for` 循环里调用 Mapper。
- 是否存在集合结果被逐条删除、逐条更新、逐条查询。
- 是否可以改成一次 Redis 调用、一次 Caffeine 批量操作、一次 SQL。
- 是否误改了已有中文注释。

已有中文注释不要随意修改。只有业务含义确实变更，才允许同步修改对应注释。
