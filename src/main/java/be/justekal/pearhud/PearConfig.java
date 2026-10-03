package be.justekal.pearhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** Config : .minecraft/config/pearhud.json */
public final class PearConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pearhud.json");

    /** Cote de l'ecran ou le HUD se range. */
    public enum Side { LEFT, RIGHT }

    /** Style d'affichage du HUD. toString() = nom traduit (utilise par l'ecran de config). */
    public enum Style {
        FULL, COMPACT, MINIMAL;

        @Override
        public String toString() {
            return Component.translatable("config.pearhud.style." + name()).getString();
        }
    }

    /** Theme de couleurs. CUSTOM utilise la couleur d'accent choisie (accentColor). */
    public enum Theme {
        //         fond      alpha  titre     sous-titre  discret   accent
        DARK  (0x000000, 0x99, 0xFFFFFF, 0xBBBBBB, 0x999999, 0xE0443C),
        LIGHT (0xF2F2F2, 0xD0, 0x1A1A1A, 0x444444, 0x666666, 0xE0443C),
        PEAR  (0x0F2A12, 0xB0, 0xF2FFE6, 0xC5E8A8, 0x9CC27A, 0x8BC34A),
        OCEAN (0x0B1F33, 0xB0, 0xEAF6FF, 0xA9D1F0, 0x7FA8C9, 0x2D9CDB),
        CUSTOM(0x000000, 0x99, 0xFFFFFF, 0xBBBBBB, 0x999999, 0xE0443C);

        public final int bg, bgAlpha, title, sub, muted, accent;

        Theme(int bg, int bgAlpha, int title, int sub, int muted, int accent) {
            this.bg = bg; this.bgAlpha = bgAlpha;
            this.title = title; this.sub = sub; this.muted = muted; this.accent = accent;
        }

        @Override
        public String toString() {
            return Component.translatable("config.pearhud.theme." + name()).getString();
        }
    }

    public boolean enabled = true;
    public String host = "127.0.0.1";
    public int port = 26538;          // port par defaut du plugin API Server
    public String authId = "minecraft-pear-hud";
    public String accessToken = "";   // rempli automatiquement apres autorisation

    // Position : x = marge par rapport au cote choisi (side), y = marge par rapport au haut
    public int x = 6;
    public int y = 6;
    public int width = 190;

    // Apparence
    public Style style = Style.FULL;    // FULL, COMPACT ou MINIMAL
    public Theme theme = Theme.DARK;    // DARK, LIGHT, PEAR, OCEAN ou CUSTOM
    public int accentColor = 0xE0443C;  // couleur d'accent RGB, utilisee avec le theme CUSTOM
    public int opacity = 100;           // opacite du fond, 10..100 (%)

    // Rangement automatique
    public boolean autoHide = true;     // true : le HUD se range sur le cote, false : toujours affiche
    public int displaySeconds = 5;      // duree d'affichage apres une touche / un changement (1..60)
    public Side side = Side.LEFT;       // LEFT ou RIGHT

    // Divers
    public boolean hideInDebug = true;  // cache le HUD quand l'ecran F3 est ouvert
    public int scrollSpeed = 25;        // vitesse du texte defilant (pixels / seconde)

    public static PearConfig load() {
        try {
            if (Files.exists(PATH)) {
                PearConfig c = GSON.fromJson(Files.readString(PATH), PearConfig.class);
                if (c != null) {
                    c.save(); // re-ecrit le fichier pour y ajouter les nouvelles options
                    return c;
                }
            }
        } catch (Exception e) {
            PearHudClient.LOGGER.warn("Config illisible, valeurs par defaut", e);
        }
        PearConfig c = new PearConfig();
        c.save();
        return c;
    }

    public synchronized void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            PearHudClient.LOGGER.warn("Impossible d'ecrire la config", e);
        }
    }
}
