# Pear HUD (Fabric, Minecraft 26.3)

Affiche la musique en cours de Pear Desktop (>= 3.12.0) en haut a gauche du HUD.

## Cote Pear Desktop
1. Parametres > Plugins > **API Server** > activer (port par defaut 26538, HTTP).
2. Lance Minecraft : une popup Pear demande d'autoriser "minecraft-pear-hud" -> Autoriser.
   Le token est stocke dans `.minecraft/config/pearhud.json`.

## Build
Copie `gradlew`, `gradlew.bat` et le dossier `gradle/` depuis le fabric-example-mod (branche 26.x), puis :
    ./gradlew build      (JDK 25 requis)
Le jar est dans build/libs/. Mets-le dans mods/ avec Fabric API 0.161.0+26.3 (ou plus recent).

## Config (config/pearhud.json)
enabled, host, port, authId, x, y, width
