package be.justekal.pearhud;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Point d'entree "modmenu" : ajoute le bouton de configuration dans Mod Menu.
 * Si YACL n'est pas installe, aucun bouton n'est ajoute (la config reste editable dans pearhud.json).
 */
public class PearModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return ModMenuApi.super.getModConfigScreenFactory();
        }
        return parent -> PearConfigScreen.create(parent, PearHudClient.config());
    }
}
