package be.justekal.pearhud;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ecran de configuration (YACL), ouvert depuis Mod Menu.
 * Seule classe du mod a importer dev.isxander.yacl3 : le HUD n'en depend pas.
 * Les textes sont dans assets/pearhud/lang (cles config.pearhud.*).
 */
public final class PearConfigScreen {
    private PearConfigScreen() {}

    public static Screen create(Screen parent, PearConfig c) {
        PearConfig d = new PearConfig(); // valeurs par defaut (bouton "reinitialiser")

        ConfigCategory general = ConfigCategory.createBuilder()
                .name(tr("category.general"))
                .option(bool("enabled", d.enabled, () -> c.enabled, v -> c.enabled = v))
                .option(bool("hideInDebug", d.hideInDebug, () -> c.hideInDebug, v -> c.hideInDebug = v))
                .build();

        ConfigCategory dock = ConfigCategory.createBuilder()
                .name(tr("category.dock"))
                .option(bool("autoHide", d.autoHide, () -> c.autoHide, v -> c.autoHide = v))
                .option(slider("displaySeconds", d.displaySeconds, 1, 60, 1,
                        () -> c.displaySeconds, v -> c.displaySeconds = v))
                .option(bool("rightSide", d.side == PearConfig.Side.RIGHT,
                        () -> c.side == PearConfig.Side.RIGHT,
                        v -> c.side = v ? PearConfig.Side.RIGHT : PearConfig.Side.LEFT))
                .build();

        ConfigCategory position = ConfigCategory.createBuilder()
                .name(tr("category.position"))
                .option(slider("x", d.x, 0, 300, 1, () -> c.x, v -> c.x = v))
                .option(slider("y", d.y, 0, 300, 1, () -> c.y, v -> c.y = v))
                .option(slider("width", d.width, 100, 400, 5, () -> c.width, v -> c.width = v))
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
                        .controller(o -> IntegerFieldControllerBuilder.create(o).range(1, 65535))
                        .build())
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(tr("title"))
                .category(general)
                .category(dock)
                .category(position)
                .category(text)
                .category(connection)
                .save(c::save)
                .build()
                .generateScreen(parent);
    }

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
        return Option.<Integer>createBuilder()
                .name(tr(key))
                .description(OptionDescription.of(tr(key + ".desc")))
                .binding(def, get, set)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(step))
                .build();
    }
}
