package be.justekal.pearhud;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

public class PearHudClient implements ClientModInitializer {
    public static final String MOD_ID = "pearhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Identifier COVER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "cover");
    private static final int COVER = 36;      // taille affichee (px GUI)
    private static final int TEX = 64;        // taille de la texture

    private static PearConfig config;
    private static PearApi api;

    private static String loadedCoverUrl = "";
    private static boolean hasCover = false;

    @Override
    public void onInitializeClient() {
        config = PearConfig.load();
        api = new PearApi(config);
        api.start();

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "now_playing"),
                PearHudClient::extract);
    }

    /** Appelee sur le thread de rendu : cree la texture quand une nouvelle pochette est prete. */
    private static void updateCover(Minecraft mc) {
        PearApi.Cover c = api.cover();
        String want = c == null ? "" : c.url();
        if (want.equals(loadedCoverUrl)) return;
        loadedCoverUrl = want;
        hasCover = false;
        if (c == null) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(c.png()));
            mc.getTextureManager().register(COVER_ID, new DynamicTexture(() -> "pearhud_cover", img));
            hasCover = true;
        } catch (Exception e) {
            LOGGER.warn("Texture de pochette impossible", e);
        }
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!config.enabled) return;

        Font font = mc.font;
        int x = config.x, y = config.y, w = config.width, h = 44;
        PearApi.Song s = api.song();
        PearApi.Status st = api.status();

        g.fill(x, y, x + w, y + h, 0x99000000);          // fond
        g.fill(x, y, x + 2, y + h, 0xFFE0443C);          // liseré

        if (s == null || st != PearApi.Status.OK) {
            g.text(font, "Pear Desktop", x + 6, y + 6, 0xFFFFFFFF, true);
            g.text(font, statusText(st), x + 6, y + 18, 0xFFAAAAAA, false);
            return;
        }

        updateCover(mc);
        int textX = x + 6;
        if (hasCover) {
            g.blit(RenderPipelines.GUI_TEXTURED, COVER_ID, x + 5, y + 4, 0, 0, COVER, COVER, TEX, TEX, TEX, TEX);
            textX = x + 5 + COVER + 6;
        }
        int textW = x + w - 6 - textX;

        String icon = s.paused() ? "|| " : "> ";
        g.text(font, fit(font, icon + s.title(), textW), textX, y + 5, 0xFFFFFFFF, true);
        g.text(font, fit(font, s.artist(), textW), textX, y + 16, 0xFFBBBBBB, false);

        double el = s.currentElapsed();
        String time = fmt(el) + " / " + fmt(s.duration());
        g.text(font, time, textX, y + 27, 0xFF999999, false);

        // barre de progression
        double ratio = s.duration() > 0 ? Math.min(1.0, el / s.duration()) : 0;
        int barX = textX, barW = textW, barY = y + h - 6;
        g.fill(barX, barY, barX + barW, barY + 2, 0x66FFFFFF);
        g.fill(barX, barY, barX + (int) (barW * ratio), barY + 2, 0xFFE0443C);
    }

    private static String fmt(double seconds) {
        int t = (int) Math.max(0, seconds);
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }

    private static String statusText(PearApi.Status st) {
        return switch (st) {
            case CONNECTING -> "Connexion...";
            case WAITING_AUTH -> "Autorise l'acces dans Pear";
            case DENIED -> "Acces refuse (nouvel essai 30s)";
            case OFFLINE -> "Pear injoignable (API Server ?)";
            case NO_SONG -> "Aucune musique";
            case OK -> "";
        };
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ell = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ell) > maxWidth) end--;
        return text.substring(0, end) + ell;
    }
}
