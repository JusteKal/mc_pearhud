package be.justekal.pearhud;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PearHudClient implements ClientModInitializer {
    public static final String MOD_ID = "pearhud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Identifier COVER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "cover");
    private static final int COVER = 36;        // taille affichee de la pochette (px GUI)
    private static final int TEX = 64;          // taille de la texture
    private static final int HEIGHT = 44;       // hauteur du HUD
    private static final int LINE = 9;          // hauteur d'une ligne de texte
    private static final double SCROLL_PAUSE = 1.2; // pause (s) au debut et a la fin du defilement
    private static final float SLIDE_SPEED = 4f;    // 1 / duree de l'animation (4 -> 0,25 s)

    private static PearConfig config;
    private static PearApi api;
    private static KeyMapping toggleKey;

    // --- pochette ---
    private static String loadedCoverUrl = "";
    private static boolean hasCover = false;

    // --- affichage / rangement ---
    private static long revealUntil = 0;        // le HUD reste sorti jusqu'a cet instant (ms)
    private static boolean manualHidden = false; // utilise quand autoHide = false
    private static float slide = 0f;            // 0 = range hors ecran, 1 = sorti
    private static long lastFrameMs = 0;

    // --- detection des changements ---
    private static String lastTrackKey = "";
    private static boolean lastPaused = false;
    private static long marqueeStart = 0;       // depart du defilement du texte

    // --- detection de l'ecran F3 (par reflexion, voir isDebugOverlayOpen) ---
    private static boolean dbgResolved = false;
    private static Field dbgField;
    private static Method dbgMethod;

    public static PearConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        config = PearConfig.load();
        api = new PearApi(config);
        api.start();

        // Touche configurable dans Options > Controles
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pearhud.toggle", InputConstants.KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(PearHudClient::onTick);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "now_playing"),
                PearHudClient::extract);
    }

    // ------------------------------------------------------------------
    // Logique : touche + detection des changements (20 fois / seconde)
    // ------------------------------------------------------------------
    private static void onTick(Minecraft mc) {
        long now = System.currentTimeMillis();

        while (toggleKey.consumeClick()) {
            if (config.autoHide) {
                if (isRevealed(now)) revealUntil = 0; else reveal(now);
            } else {
                manualHidden = !manualHidden;
            }
        }

        PearApi.Song s = api.song();
        if (s == null || api.status() != PearApi.Status.OK) return;

        String key = s.title() + "\u0001" + s.artist() + "\u0001" + (long) s.duration();
        if (!key.equals(lastTrackKey)) {          // nouvelle musique
            lastTrackKey = key;
            marqueeStart = now;                   // le texte defilant repart du debut
            reveal(now);
        } else if (s.paused() != lastPaused) {    // pause / reprise
            reveal(now);
        }
        lastPaused = s.paused();
    }

    private static boolean isRevealed(long now) { return now < revealUntil; }

    private static void reveal(long now) {
        int secs = Math.max(1, Math.min(60, config.displaySeconds));
        revealUntil = now + secs * 1000L;
    }

    // ------------------------------------------------------------------
    // Pochette (thread de rendu)
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------
    private static void extract(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (!config.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        // Animation de rangement : slide va vers 1 (sorti) ou 0 (range)
        float dtSec = lastFrameMs == 0 ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        boolean wantShown = config.autoHide ? isRevealed(now) : !manualHidden;
        float step = dtSec * SLIDE_SPEED;
        slide = wantShown ? Math.min(1f, slide + step) : Math.max(0f, slide - step);
        if (slide <= 0f) return;                                  // range : rien a dessiner
        if (config.hideInDebug && isDebugOverlayOpen(mc)) return; // pas d'affichage dans F3

        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int w = Math.max(100, config.width);
        int h = HEIGHT;
        boolean right = config.side == PearConfig.Side.RIGHT;

        // Position : le HUD glisse hors de l'ecran, du cote choisi
        float eased = slide * slide * (3f - 2f * slide);
        int shift = Math.round((w + config.x + 4) * (1f - eased));
        int x = right ? sw - w - config.x + shift : config.x - shift;
        int y = config.y;

        PearApi.Song s = api.song();
        PearApi.Status st = api.status();

        g.fill(x, y, x + w, y + h, 0x99000000);                       // fond
        g.fill(right ? x + w - 2 : x, y, right ? x + w : x + 2, y + h, 0xFFE0443C); // liseré

        if (s == null || st != PearApi.Status.OK) {
            g.text(font, "Pear Desktop", x + 6, y + 6, 0xFFFFFFFF, true);
            g.text(font, fit(font, statusText(st), w - 14), x + 6, y + 18, 0xFFAAAAAA, false);
            return;
        }

        updateCover(mc);
        int textX = x + 6;
        if (hasCover) {
            g.blit(RenderPipelines.GUI_TEXTURED, COVER_ID, x + 5, y + 4, 0, 0, COVER, COVER, TEX, TEX, TEX, TEX);
            textX = x + 5 + COVER + 6;
        }
        int textW = x + w - 6 - textX;

        // Titre : icone fixe + titre defilant
        String icon = s.paused() ? "|| " : "> ";
        int iconW = font.width(icon);
        g.text(font, icon, textX, y + 5, 0xFFFFFFFF, true);
        drawScrolling(g, font, s.title(), textX + iconW, y + 5, textW - iconW, 0xFFFFFFFF, true, now, sw);

        // Artiste defilant
        drawScrolling(g, font, s.artist(), textX, y + 16, textW, 0xFFBBBBBB, false, now, sw);

        double el = s.currentElapsed();
        g.text(font, fmt(el) + " / " + fmt(s.duration()), textX, y + 27, 0xFF999999, false);

        // barre de progression
        double ratio = s.duration() > 0 ? Math.min(1.0, el / s.duration()) : 0;
        int barY = y + h - 6;
        g.fill(textX, barY, textX + textW, barY + 2, 0x66FFFFFF);
        g.fill(textX, barY, textX + (int) (textW * ratio), barY + 2, 0xFFE0443C);
    }

    /**
     * Texte qui defile quand il est trop long : pause au debut, defilement vers la gauche
     * jusqu'a la fin, pause, puis retour au debut. Si le texte tient, il est affiche tel quel.
     */
    private static void drawScrolling(GuiGraphicsExtractor g, Font font, String text, int x, int y,
                                      int maxW, int color, boolean shadow, long now, int screenW) {
        if (maxW <= 0) return;
        int textW = font.width(text);
        if (textW <= maxW) {
            g.text(font, text, x, y, color, shadow);
            return;
        }

        int overflow = textW - maxW;
        double speed = Math.max(5, config.scrollSpeed);       // px / s
        double travel = overflow / speed;
        double cycle = SCROLL_PAUSE * 2 + travel;
        double t = ((now - marqueeStart) / 1000.0) % cycle;
        double offset = t < SCROLL_PAUSE ? 0
                : t < SCROLL_PAUSE + travel ? (t - SCROLL_PAUSE) * speed
                : overflow;

        // Zone de decoupe limitee a l'ecran (le HUD peut etre partiellement hors ecran pendant l'animation)
        int x0 = Math.max(0, x);
        int x1 = Math.min(screenW, x + maxW);
        if (x1 <= x0) return;
        g.enableScissor(x0, Math.max(0, y - 1), x1, y + LINE + 1);
        g.text(font, text, x - (int) Math.round(offset), y, color, shadow);
        g.disableScissor();
    }

    // ------------------------------------------------------------------
    // Ecran F3
    // ------------------------------------------------------------------
    /**
     * Depuis la 1.21.9 l'etat de l'ecran F3 est porte par Minecraft#debugEntries
     * (DebugScreenEntryList#isOverlayVisible). On y accede par reflexion : si le nom change
     * dans une version future, le mod continue de fonctionner (le HUD reste simplement affiche).
     */
    private static boolean isDebugOverlayOpen(Minecraft mc) {
        if (!dbgResolved) {
            dbgResolved = true;
            try {
                Field f = Minecraft.class.getDeclaredField("debugEntries");
                f.setAccessible(true);
                Method m = f.getType().getDeclaredMethod("isOverlayVisible");
                m.setAccessible(true);
                dbgField = f;
                dbgMethod = m;
            } catch (Exception e) {
                LOGGER.warn("Detection de l'ecran F3 indisponible ({}): le HUD restera affiche avec F3.", e.toString());
            }
        }
        if (dbgMethod == null) return false;
        try {
            return (boolean) dbgMethod.invoke(dbgField.get(mc));
        } catch (Exception e) {
            dbgMethod = null;
            LOGGER.warn("Detection de l'ecran F3 desactivee: {}", e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------
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
