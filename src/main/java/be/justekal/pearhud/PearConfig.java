package be.justekal.pearhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Config : .minecraft/config/pearhud.json */
public final class PearConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pearhud.json");

    /** Cote de l'ecran ou le HUD se range. */
    public enum Side { LEFT, RIGHT }

    public boolean enabled = true;
    public String host = "127.0.0.1";
    public int port = 26538;          // port par defaut du plugin API Server
    public String authId = "minecraft-pear-hud";
    public String accessToken = "";   // rempli automatiquement apres autorisation

    // Position : x = marge par rapport au cote choisi (side), y = marge par rapport au haut
    public int x = 6;
    public int y = 6;
    public int width = 190;

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
