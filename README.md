# OldRaids
(English)

**WARNING!
The text content of this `README.md` and the Releases is generated and modified by AI tools. It may differ from the official stance of the OldRaids mod and could contain unclear phrasing.**

**Targets Minecraft 1.21.4 Leaf/Paper/Spigot-compatible servers.**

A lightweight Spigot plugin for 1.21.4 that reverts raid behavior to before 1.21, allowing old raid farms to work the same as before:

- killing a raid captain gives Bad Omen again
- Bad Omen can directly trigger or extend a raid in a village
- raid waves are moved back toward the pre-1.21.3 spawn-location pattern
- moved raid wave positions are validated with vanilla Ravager spawn-placement rules

If you want to try or test it, you can to join the server: `trap.786913.xyz`

### Build

```bash
mvn -DskipTests package
```

The compiled jar is written to `target/oldraids-1.21.4-1.2.jar`.

### Special Thanks

- [XUANHLGG](https://github.com/XUANHLGG) for providing valuable testing opportunities and minor code changes.

---

# OldRaids
(简体中文)

**警告！
此 `README.md` 和 Releases 的文本内容由 AI 工具生成和修改，可能与 OldRaids 模组的官方立场有所不同，且部分表述可能不够清晰。**

**适用于 Minecraft 1.21.4 Leaf/Paper/Spigot 兼容服务端。**

一个适用于 1.21.4 的轻量化 Spigot 插件，它将袭击机制恢复到 1.21 之前，让旧版袭击农场能够像以前一样正常工作：

- 击杀袭击队长会再次给予“不祥之兆”效果
- 不祥之兆可以直接在村庄中触发或延长袭击
- 袭击波次的生成位置恢复到 1.21.3 之前的生成模式
- 调整后的袭击波次生成位置会通过原版劫掠兽的生成规则进行有效性验证

如果你想尝试/测试，可以加入 `trap.786913.xyz` 这个服务器。

### 构建

```bash
mvn -DskipTests package
```

编译完成的 jar 包将输出至 `target/oldraids-1.21.4-1.2.jar`。

### 特别鸣谢

- [XUANHLGG](https://github.com/XUANHLGG) 提供了宝贵的测试机会和少量的代码更改。
