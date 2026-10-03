package be.justekal.pearhud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Dessin du HUD (3 styles, 5 themes). Partage entre le HUD en jeu (PearHudClient)
 * et l'apercu de l'ecran de configuration (PearConfigScreen) : les deux appellent draw().
 */
public final class HudRenderer {
    private HudRenderer() {}

    private static final int TEX = 64;                 // taille de la texture de pochette
    private static final int LINE = 9;                 // hauteur d'une ligne de texte
    private static final double SCROLL_PAUSE = 1.2;    // pause (s) au debut et a la fin du defilement

    /** Apparence a dessiner (valeurs de la config ou valeurs en cours d'edition dans l'apercu). */
    public record Look(PearConfig.Style style, PearConfig.Theme theme, int accent, int opacity,
                       int width, boolean rightEdge) {
        public static Look of(PearConfig c) {
            return new Look(c.style, c.theme, c.accentColor, c.opacity, c.width, c.side == PearConfig.Side.RIGHT);
        }

        public Look withWidth(int w) {
            return new Look(style, theme, accent, opacity, w, rightEdge);
        }
    }

    /** Musique a afficher. */
    public record Track(String title, String artist, boolean paused, double elapsed, double duration) {}

    /** Dimensions du HUD (en pixels GUI). */
    public record Size(int w, int h) {}

    /** Donnees d'animation du texte defilant. */
    public record Anim(long now, long start, int scrollSpeed, int screenW) {}

    private record Colors(int bg, int title, int sub, int muted, int accent) {}

    // ------------------------------------------------------------------
    // Dimensions
    // ------------------------------------------------------------------
    /** track == null : le HUD affiche un message d'etat (status) a la place de la musique. */
    public static Size size(Font font, Look look, Track track, String status) {
        int w = Math.max(100, Math.min(400, look.width()));
        return switch (style(look)) {
            case COMPACT -> new Size(w, 18);
            case MINIMAL -> {
                int natural = track == null
                        ? font.width("Pear - " + status)
                        : font.width(icon(track)) + font.width(titleArtist(track)) + font.width(timeText(track)) + 6;
                yield new Size(Math.max(20, Math.min(natural, w)), track != null ? 12 : 9);
            }
            default -> new Size(w, 44);
        };
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------
    /**
     * @param showCover true : reserve la place de la pochette
     * @param cover     texture de la pochette, ou null pour dessiner un carre de remplacement
     */
    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, Size size, Look look,
                            Track t, String status, boolean showCover, Identifier cover, Anim anim) {
        Colors col = colors(look);
        switch (style(look)) {
            case COMPACT -> drawCompact(g, font, x, y, size, look, col, t, status, showCover, cover, anim);
            case MINIMAL -> drawMinimal(g, font, x, y, size, col, t, status, anim);
            default -> drawFull(g, font, x, y, size, look, col, t, status, showCover, cover, anim);
        }
    }

    // ---------- Style COMPLET : pochette + titre + artiste + duree + barre ----------
    private static void drawFull(GuiGraphicsExtractor g, Font font, int x, int y, Size size, Look look,
                                 Colors col, Track t, String status, boolean showCover, Identifier cover, Anim anim) {
        int w = size.w(), h = size.h();
        background(g, x, y, w, h, look, col);

        if (t == null) {
            g.text(font, "Pear Desktop", x + 6, y + 6, col.title(), true);
            g.text(font, fit(font, status, w - 14), x + 6, y + 18, col.sub(), false);
            return;
        }

        int textX = x + 6;
        if (showCover) {
            drawCover(g, cover, x + 5, y + 4, 36, col);
            textX = x + 5 + 36 + 6;
        }
        int textW = x + w - 6 - textX;

        String icon = icon(t);
        int iconW = font.width(icon);
        g.text(font, icon, textX, y + 5, col.title(), true);
        drawScrolling(g, font, t.title(), textX + iconW, y + 5, textW - iconW, col.title(), true, anim);
        drawScrolling(g, font, t.artist(), textX, y + 16, textW, col.sub(), false, anim);

        g.text(font, fmt(t.elapsed()) + " / " + fmt(t.duration()), textX, y + 27, col.muted(), false);
        drawBar(g, textX, y + h - 6, textW, 2, t, col);
    }

    // ---------- Style COMPACT : une ligne ----------
    private static void drawCompact(GuiGraphicsExtractor g, Font font, int x, int y, Size size, Look look,
                                    Colors col, Track t, String status, boolean showCover, Identifier cover, Anim anim) {
        int w = size.w(), h = size.h();
        background(g, x, y, w, h, look, col);

        if (t == null) {
            g.text(font, fit(font, "Pear - " + status, w - 12), x + 6, y + 3, col.sub(), false);
            return;
        }

        int tx = x + 5;
        if (showCover) {
            drawCover(g, cover, tx, y + 2, 12, col);
            tx += 12 + 4;
        }
        String time = fmt(t.elapsed()) + "/" + fmt(t.duration());
        int right = x + w - 4;
        String icon = icon(t);
        int iconW = font.width(icon);
        g.text(font, icon, tx, y + 3, col.title(), true);

        String line = titleArtist(t);
        int textX = tx + iconW;
        int textW = right - (font.width(time) + 6) - textX;
        drawScrolling(g, font, line, textX, y + 3, textW, col.title(), true, anim);
        g.text(font, time, right - font.width(time), y + 3, col.muted(), false);

        drawBar(g, x, y + h - 2, w, 2, t, col);
    }

    // ---------- Style MINIMAL : texte seul, sans fond ----------
    private static void drawMinimal(GuiGraphicsExtractor g, Font font, int x, int y, Size size,
                                    Colors col, Track t, String status, Anim anim) {
        int w = size.w();
        if (t == null) {
            g.text(font, fit(font, "Pear - " + status, w), x, y, 0xFFFFFFFF, true);
            return;
        }
        String time = timeText(t);
        int timeW = font.width(time) + 6;
        String icon = icon(t);
        int iconW = font.width(icon);
        g.text(font, icon, x, y, 0xFFFFFFFF, true);
        drawScrolling(g, font, titleArtist(t), x + iconW, y, w - iconW - timeW, 0xFFFFFFFF, true, anim);
        g.text(font, time, x + w - font.width(time), y, 0xFFBBBBBB, true);
        drawBar(g, x, y + 10, w, 1, t, col);
    }

    // ------------------------------------------------------------------
    // Briques communes
    // ------------------------------------------------------------------
    private static void background(GuiGraphicsExtractor g, int x, int y, int w, int h, Look look, Colors col) {
        g.fill(x, y, x + w, y + h, col.bg());
        g.fill(look.rightEdge() ? x + w - 2 : x, y, look.rightEdge() ? x + w : x + 2, y + h, col.accent());
    }

    private static void drawCover(GuiGraphicsExtractor g, Identifier cover, int x, int y, int size, Colors col) {
        if (cover != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, cover, x, y, 0, 0, size, size, TEX, TEX, TEX, TEX);
        } else { // pas de pochette disponible (apercu) : carre aux couleurs du theme
            g.fill(x, y, x + size, y + size, (0x66 << 24) | (col.accent() & 0xFFFFFF));
        }
    }

    private static void drawBar(GuiGraphicsExtractor g, int x, int y, int w, int hh, Track t, Colors col) {
        double ratio = t.duration() > 0 ? Math.min(1.0, t.elapsed() / t.duration()) : 0;
        g.fill(x, y, x + w, y + hh, 0x66FFFFFF);
        g.fill(x, y, x + (int) (w * ratio), y + hh, col.accent());
    }

    /**
     * Texte qui defile quand il est trop long : pause au debut, defilement vers la gauche
     * jusqu'a la fin, pause, puis retour au debut. Si le texte tient, il est affiche tel quel.
     */
    private static void drawScrolling(GuiGraphicsExtractor g, Font font, String text, int x, int y,
                                      int maxW, int color, boolean shadow, Anim anim) {
        if (maxW <= 0) return;
        int textW = font.width(text);
        if (textW <= maxW) {
            g.text(font, text, x, y, color, shadow);
            return;
        }

        int overflow = textW - maxW;
        double speed = Math.max(5, anim.scrollSpeed());       // px / s
        double travel = overflow / speed;
        double cycle = SCROLL_PAUSE * 2 + travel;
        double t = ((anim.now() - anim.start()) / 1000.0) % cycle;
        double offset = t < SCROLL_PAUSE ? 0
                : t < SCROLL_PAUSE + travel ? (t - SCROLL_PAUSE) * speed
                : overflow;

        // Zone de decoupe limitee a l'ecran (le HUD peut etre partiellement hors ecran pendant l'animation)
        int x0 = Math.max(0, x);
        int x1 = Math.min(anim.screenW(), x + maxW);
        if (x1 <= x0) return;
        g.enableScissor(x0, Math.max(0, y - 1), x1, y + LINE + 1);
        g.text(font, text, x - (int) Math.round(offset), y, color, shadow);
        g.disableScissor();
    }

    // ------------------------------------------------------------------
    // Couleurs et textes
    // ------------------------------------------------------------------
    private static PearConfig.Style style(Look look) {
        return look.style() == null ? PearConfig.Style.FULL : look.style();
    }

    private static Colors colors(Look look) {
        PearConfig.Theme th = look.theme() == null ? PearConfig.Theme.DARK : look.theme();
        int accent = th == PearConfig.Theme.CUSTOM ? look.accent() : th.accent;
        int op = Math.max(0, Math.min(100, look.opacity()));
        int alpha = Math.round(th.bgAlpha * op / 100f);
        return new Colors((alpha << 24) | th.bg,
                0xFF000000 | th.title, 0xFF000000 | th.sub, 0xFF000000 | th.muted,
                0xFF000000 | (accent & 0xFFFFFF));
    }

    private static String icon(Track t) {
        return t.paused() ? "|| " : "> ";
    }

    private static String titleArtist(Track t) {
        return t.title() + (t.artist().isBlank() ? "" : " - " + t.artist());
    }

    private static String timeText(Track t) {
        return fmt(t.elapsed()) + "/" + fmt(t.duration());
    }

    private static String fmt(double seconds) {
        int t = (int) Math.max(0, seconds);
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ell = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ell) > maxWidth) end--;
        return text.substring(0, end) + ell;
    }
}
