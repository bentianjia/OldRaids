# OldRaids

**WARNING!
The text content of this `README.md` and the Releases is generated and modified by AI tools. It may differ from the official stance of the OldRaids mod and could contain unclear phrasing.**

**Targets Minecraft 1.21.4 Leaf/Paper/Spigot-compatible servers.**

A lightweight Spigot plugin for 1.21.4 that reverts raid behavior to before 1.21, allowing old raid farms to work the same as before:

- killing a raid captain gives Bad Omen again
- Bad Omen can directly trigger or extend a raid in a village
- raid waves are moved back toward the pre-1.21.3 spawn-location pattern
- moved raid wave positions are validated with vanilla Ravager spawn-placement rules
- configurable ominous bottle drops with a persistent `config.yml`

### Configuration

The plugin creates `plugins/OldRaids/config.yml` on first start.

```yaml
keep-ominous-bottle: false
```

Use `/oldraids set keep-ominous-bottle true` to keep ominous bottle drops, or `/oldraids reload` after editing the file manually.

### Build

```bash
mvn -DskipTests package
```

The compiled jar is written to `target/oldraids-1.21.4-1.4.jar`.

### Special Thanks

- [XUANHLGG](https://github.com/XUANHLGG) for providing valuable testing opportunities and partial code changes.

---

# OldRaids (中文说明)

**警告！
此 `README.md` 和 Releases 的文本内容由 AI 工具生成和修改，可能与 OldRaids 模组的官方立场有所不同，且部分表述可能不够清晰。**

**适用于 Minecraft 1.21.4 Leaf/Paper/Spigot 兼容服务端。**

一个适用于 1.21.4 的轻量化 Spigot 插件，它将袭击机制恢复到 1.21 之前，让旧版袭击农场能够像以前一样正常工作：

- 击杀袭击队长会再次给予“不祥之兆”效果
- 不祥之兆可以直接在村庄中触发或延长袭击
- 袭击波次的生成位置恢复到 1.21.3 之前的生成模式
- 调整后的袭击波次生成位置会通过原版劫掠兽的生成规则进行有效性验证
- 可通过持久化 `config.yml` 配置是否保留不祥之瓶掉落

### 配置

插件首次启动时会生成 `plugins/OldRaids/config.yml`。

```yaml
keep-ominous-bottle: false
```

如果要保留不祥之瓶掉落，可以使用 `/oldraids set keep-ominous-bottle true`；手动编辑配置后可使用 `/oldraids reload` 重新加载。

### 构建

```bash
mvn -DskipTests package
```

编译完成的 jar 包将输出至 `target/oldraids-1.21.4-1.4.jar`。

### 特别鸣谢

- [XUANHLGG](https://github.com/XUANHLGG) 提供了宝贵的测试机会和部分代码更改。
