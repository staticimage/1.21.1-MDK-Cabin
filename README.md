# Cabin Mod

Cabin Mod rebuilt from scratch for NeoForge. Created by staticimage, with art and design help from orbiez_.

Minecraft **1.21.1**, NeoForge **21.1.164**, Java **21**.

## Build and run

```powershell
./gradlew.bat build              # JAR ends up in build/libs/
./gradlew.bat runClient          # dev client
./gradlew.bat runServer          # dev dedicated server
./gradlew.bat runGameTestServer  # run all GameTests, then exit
./gradlew.bat runData            # regenerate src/generated/resources
```

## In game

To spawn the Jehoash rave arena, centered on you (it needs a clear 25 x 9 x 25 area):

```mcfunction
/cabinmod rave_arena ~ ~ ~
```

## Layout

All code lives in `src/main/java/net/austin/cabinmod/`:

- `block`, `item`, `entity`, `effect`, `sounds`, `villager`, `worldgen`, `trim`: registered content
- `client`: renderers, models, screens and other client-only code
- `network`: packets
- `event`: game event handlers
- `datagen`: data generators (output goes to `src/generated/resources`)
- `gametest`: automated GameTests

Textures, models, sounds and shaders are in `src/main/resources/assets/cabinmod/`.
