# OldRaids

**Targets Minecraft 1.21.4 Leaf/Paper/Spigot-compatible servers.**

A lightweight plugin that restores old raid behavior for 1.21.4 raid farms:

- killing a raid captain gives Bad Omen again
- Bad Omen can directly trigger or extend a raid in a village
- raid waves are moved back toward the pre-1.21.3 spawn-location pattern
- moved raid wave positions are validated with vanilla Ravager spawn-placement rules

### Build

```
mvn -DskipTests package
```

The compiled jar is written to `target/oldraids-1.21.4-1.2.jar`.
