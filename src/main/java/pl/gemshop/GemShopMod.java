package pl.gemshop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GemShopMod implements ClientModInitializer {
    public static ModConfig cfg;
    public static KeyBinding openKey, lookKey;

    @Override
    public void onInitializeClient() {
        cfg = ModConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gemshop.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.gemshop"));
        lookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gemshop.look", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.gemshop"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new GemShopScreen());
            }
            while (lookKey.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) {
                    AbstractClientPlayerEntity t = Target.lookedAt(1f);
                    if (t != null) mc.setScreen(new GemShopScreen(t.getUuid(), t.getNameForScoreboard()));
                    else mc.player.sendMessage(Text.literal("§7Nie patrzysz na zadnego gracza."), true);
                }
            }
        });
    }
}
