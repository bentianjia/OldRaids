# OldRaids

[简体中文](README_CN.md)

A plugin tailored for 1.21.4 that reverts raid mechanics to pre-1.21 behavior, allowing legacy raid farms to function properly again, while adding several technical-oriented features:

- Killing a Raid Captain grants the "Bad Omen" effect once again.
- Bad Omen can directly trigger or prolong a raid inside a village.
- Raid wave spawn locations are restored to the pre-1.21.3 spawning model.
- Raid wave spawn locations are validated using vanilla Ravager spawning rules.
- Removes Ominous Bottle drops.
- Totems of Undying dropped during raids are fireproof, just like netherite items.

Personal Note: If you want to test it out, feel free to join `trap.786913.xyz`.

### Configuration

The plugin does not provide any configuration files or commands. Don't like it? Deal with it.

### Building

```bash
mvn -DskipTests package
```

The compiled jar file will be output to `target/oldraids-1.21.4-1.6.jar`.

### Special Thanks
- [XUANHLGG](https://github.com/XUANHLGG) for providing valuable testing opportunities and code modifications.
