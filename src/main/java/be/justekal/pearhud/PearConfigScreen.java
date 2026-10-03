package be.justekal.pearhud;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ecran de configuration (YACL), ouvert depuis Mod Menu.
 * Seule classe du mod a importer dev.isxander.yacl3 : le HUD n'en depend pas.
 * Les textes sont dans assets/pearhud/lang (cles config.pearhud.*).
 *
 * L'onglet "Apparence" affiche un apercu en direct du HUD dans le panneau de description
 * (a droite) quand on survole ou selectionne une de ses options.
 */
public final class PearConfigScreen {
    private PearConfigScreen() {}

    /** Options dont la valeur en cours d'edition (non enregistree) alimente l'apercu. */
    private static final class Opts {
        Option<PearConfig.Style> style;
        Option<PearConfig.Theme> theme;
        Option<Color> accent;
        Option<Integer> opacity;
        Option<Integer> width;
        Option<Boolean> right;
    }

    public static Screen create(Screen parent, PearConfig c) {
        PearConfig d = new PearConfig(); // valeurs par defaut (bouton "reinitialiser")
        Opts o = new Opts();
        Preview preview = new Preview(() -> currentLook(o, c), c);

        ConfigCategory general = ConfigCategory.createBuilder()
                .name(tr("category.general"))
                .option(bool("enabled", d.enabled, () -> c.enabled, v -> c.enabled = v))
                .option(bool("hideInDebug", d.hideInDebug, () -> c.hideInDebug, v -> c.hideInDebug = v))
                .build();

        o.style = Option.<PearConfig.Style>createBuilder()
                .name(tr("style"))
                .description(withPreview("style", preview))
                .binding(d.style, () -> c.style, v -> c.style = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(PearConfig.Style.class))
                .build();

        o.theme = Option.<PearConfig.Theme>createBuilder()
                .name(tr("theme"))
                .description(withPreview("theme", preview))
                .binding(d.theme, () -> c.theme, v -> c.theme = v)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(PearConfig.Theme.class))
                .build();

        o.accent = Option.<Color>createBuilder()
                .name(tr("accentColor"))
                .description(withPreview("accentColor", preview))
                .binding(new Color(d.accentColor), () -> new Color(c.accentColor),
                        v -> c.accentColor = v.getRGB() & 0xFFFFFF)
                .controller(ColorControllerBuilder::create)
                .build();

        o.opacity = slider("opacity", d.opacity, 10, 100, 5,
                () -> c.opacity, v -> c.opacity = v, withPreview("opacity", preview));

        ConfigCategory appearance = ConfigCategory.createBuilder()
                .name(tr("category.appearance"))
                .option(o.style)
                .option(o.theme)
                .option(o.accent)
                .option(o.opacity)
                .build();

        o.right = bool("rightSide", d.side == PearConfig.Side.RIGHT,
                () -> c.side == PearConfig.Side.RIGHT,
                v -> c.side = v ? PearConfig.Side.RIGHT : PearConfig.Side.LEFT);

        ConfigCategory dock = ConfigCategory.createBuilder()
                .name(tr("category.dock"))
                .option(bool("autoHide", d.autoHide, () -> c.autoHide, v -> c.autoHide = v))
                .option(slider("displaySeconds", d.displaySeconds, 1, 60, 1,
                        () -> c.displaySeconds, v -> c.displaySeconds = v))
                .option(o.right)
                .build();

        o.width = slider("width", d.width, 100, 400, 5, () -> c.width, v -> c.width = v);

        ConfigCategory position = ConfigCategory.createBuilder()
                .name(tr("category.position"))
                .option(slider("x", d.x, 0, 300, 1, () -> c.x, v -> c.x = v))
                .option(slider("y", d.y, 0, 300, 1, () -> c.y, v -> c.y = v))
                .option(o.width)
                .build();

        ConfigCategory text = ConfigCategory.createBuilder()
                .name(tr("category.text"))
                .option(slider("scrollSpeed", d.scrollSpeed, 5, 100, 5,
                        () -> c.scrollSpeed, v -> c.scrollSpeed = v))
                .build();

        ConfigCategory connection = ConfigCategory.createBuilder()
                .name(tr("category.connection"))
                .option(Option.<String>createBuilder()
                        .name(tr("host"))
                        .description(OptionDescription.of(tr("host.desc")))
                        .binding(d.host, () -> c.host, v -> c.host = v)
                        .controller(StringControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(tr("port"))
                        .description(OptionDescription.of(tr("port.desc")))
                        .binding(d.port, () -> c.port, v -> c.port = v)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 65535))
                        .build())
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(tr("title"))
                .category(general)
                .category(appearance)
                .category(dock)
                .category(position)
                .category(text)
                .category(connection)
                .save(c::save)
                .build()
                .generateScreen(parent);
    }

    // ------------------------------------------------------------------
    // Apercu en direct
    // ------------------------------------------------------------------

    /** Apparence correspondant aux valeurs en cours d'edition (ou a la config si les options n'existent pas encore). */
    private static HudRenderer.Look currentLook(Opts o, PearConfig c) {
        PearConfig.Style style = o.style != null ? o.style.pendingValue() : c.style;
        PearConfig.Theme theme = o.theme != null ? o.theme.pendingValue() : c.theme;
        int accent = o.accent != null ? o.accent.pendingValue().getRGB() & 0xFFFFFF : c.accentColor;
        int opacity = o.opacity != null ? o.opacity.pendingValue() : c.opacity;
        int width = o.width != null ? o.width.pendingValue() : c.width;
        boolean right = o.right != null ? o.right.pendingValue() : c.side == PearConfig.Side.RIGHT;
        return new HudRenderer.Look(style, theme, accent, opacity, width, right);
    }

    private static OptionDescription withPreview(String key, Preview preview) {
        return OptionDescription.createBuilder()
                .text(tr(key + ".desc"))
                .customImage(CompletableFuture.completedFuture(Optional.<ImageRenderer>of(preview)))
                .build();
    }

    /** Dessine le vrai HUD avec une musique d'exemple, dans le panneau de description de YACL. */
    private static final class Preview implements ImageRenderer {
        private static final double SAMPLE_DURATION = 93;
        private final Supplier<HudRenderer.Look> look;
        private final PearConfig config;
        private final long start = System.currentTimeMillis();

        Preview(Supplier<HudRenderer.Look> look, PearConfig config) {
            this.look = look;
            this.config = config;
        }

        public int render(GuiGraphicsExtractor g, int x, int y, int renderWidth, float tickDelta) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            long now = System.currentTimeMillis();

            HudRenderer.Look lk = look.get();
            lk = lk.withWidth(Math.min(lk.width(), Math.max(100, renderWidth)));

            double elapsed = ((now - start) / 1000.0) % SAMPLE_DURATION;
            HudRenderer.Track track = new HudRenderer.Track(
                    "Once Upon a Time (Extended Mix)", "Austin Farwell", false, elapsed, SAMPLE_DURATION);

            HudRenderer.Size size = HudRenderer.size(font, lk, track, "");
            HudRenderer.draw(g, font, x, y + 4, size, lk, track, "", true, PearHudClient.coverTexture(),
                    new HudRenderer.Anim(now, start, config.scrollSpeed, mc.getWindow().getGuiScaledWidth()));
            return size.h() + 8;
        }

        /** Meme methode sous le nom des methodes "extract*" de Minecraft 26.x (selon la version de YACL). */
        public int extract(GuiGraphicsExtractor g, int x, int y, int renderWidth, float tickDelta) {
            return render(g, x, y, renderWidth, tickDelta);
        }

        public void close() {}
    }

    // ------------------------------------------------------------------
    // Aides pour construire les options
    // ------------------------------------------------------------------
    private static Component tr(String key) {
        return Component.translatable("config.pearhud." + key);
    }

    private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(tr(key))
                .description(OptionDescription.of(tr(key + ".desc")))
                .binding(def, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> slider(String key, int def, int min, int max, int step,
                                          Supplier<Integer> get, Consumer<Integer> set) {
        return slider(key, def, min, max, step, get, set, OptionDescription.of(tr(key + ".desc")));
    }

    private static Option<Integer> slider(String key, int def, int min, int max, int step,
                                          Supplier<Integer> get, Consumer<Integer> set, OptionDescription desc) {
        return Option.<Integer>createBuilder()
                .name(tr(key))
                .description(desc)
                .binding(def, get, set)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }
}
