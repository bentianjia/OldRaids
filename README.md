# OldRaids
(English)

**WARNING!
The text content of this `README.md` and the Releases is generated and modified by AI tools. It may differ from the official stance of the OldRaids mod and could contain unclear phrasing.**

**Targets Minecraft 1.21.4 Leaf/Paper/Spigot-compatible servers.**

A lightweight plugin that restores old raid behavior for 1.21.4 raid farms:

- killing a raid captain gives Bad Omen again
- Bad Omen can directly trigger or extend a raid in a village
- raid waves are moved back toward the pre-1.21.3 spawn-location pattern
- moved raid wave positions are validated with vanilla Ravager spawn-placement rules

### Build

```bash
mvn -DskipTests package
```

The compiled jar is written to `target/oldraids-1.21.4-1.2.jar`.

---

# OldRaids 
(简体中文)
**警告！
此 `README.md` 和 Releases 的文本内容由 AI 工具生成和修改，可能与 OldRaids 模组的官方立场有所不同，且部分表述可能不够清晰。**

**适用于 Minecraft 1.21.4 Leaf/Paper/Spigot 兼容服务端。**

一个为 1.21.4 袭击农场恢复旧版袭击机制的轻量化插件：

- 击杀袭击队长会再次给予“不祥之兆”效果
- 不祥之兆可以直接在村庄中触发或延长袭击
- 袭击波次的生成位置恢复到 1.21.3 之前的生成模式
- 调整后的袭击波次生成位置会通过原版劫掠兽的生成规则进行有效性验证

### 构建

```bash
mvn -DskipTests package
```

编译完成的 jar 包将输出至 `target/oldraids-1.21.4-1.2.jar`。
