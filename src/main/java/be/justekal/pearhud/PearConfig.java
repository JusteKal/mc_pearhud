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

    public boolean enabled = true;
    public String host = "127.0.0.1";
    public int port = 26538;          // port par defaut du plugin API Server
    public String authId = "minecraft-pear-hud";
    public String accessToken = "";   // rempli automatiquement apres autorisation
    public int x = 6;
    public int y = 6;
    public int width = 190;

    public static PearConfig load() {
        try {
            if (Files.exists(PATH)) {
                PearConfig c = GSON.fromJson(Files.readString(PATH), PearConfig.class);
                if (c != null) return c;
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
