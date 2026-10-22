package dev.aurora.mixin;

import dev.aurora.Aurora;
import dev.aurora.gui.screen.AuroraTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Swaps the vanilla title screen for Aurora's: both when one is passed in explicitly and when
	 * a null screen outside of a world would make vanilla create one.
	 */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen aurora$titleScreen(Screen screen) {
		if (Aurora.client() == null || !Aurora.client().customMenu.on()) {
			return screen;
		}
		if (screen == null ? minecraft.level == null : screen.getClass() == TitleScreen.class) {
			return new AuroraTitleScreen();
		}
		return screen;
	}
}
