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

        PearApi.Song s = api.song();
        PearApi.Status st = api.status();
        boolean playing = s != null && st == PearApi.Status.OK;

        HudRenderer.Look look = HudRenderer.Look.of(config);
        HudRenderer.Track track = playing
                ? new HudRenderer.Track(s.title(), s.artist(), s.paused(), s.currentElapsed(), s.duration())
                : null;
        String status = statusText(st);
        HudRenderer.Size size = HudRenderer.size(font, look, track, status);

        // Position : le HUD glisse hors de l'ecran, du cote choisi
        boolean right = config.side == PearConfig.Side.RIGHT;
        float eased = slide * slide * (3f - 2f * slide);
        int shift = Math.round((size.w() + config.x + 4) * (1f - eased));
        int x = right ? sw - size.w() - config.x + shift : config.x - shift;
        int y = config.y;

        if (playing) updateCover(mc);
        HudRenderer.draw(g, font, x, y, size, look, track, status,
                playing && hasCover, hasCover ? COVER_ID : null,
                new HudRenderer.Anim(now, marqueeStart, config.scrollSpeed, sw));
    }

    /**
     * Texture de la pochette courante (ou null si aucune). Utilisee par l'apercu de l'ecran de config.
     * A appeler depuis le thread de rendu.
     */
    public static Identifier coverTexture() {
        if (api == null) return null;
        updateCover(Minecraft.getInstance());
        return hasCover ? COVER_ID : null;
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
}
