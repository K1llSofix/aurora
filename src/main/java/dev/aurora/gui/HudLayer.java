package dev.aurora.gui;

import dev.aurora.Aurora;
import dev.aurora.gui.screen.HudEditorScreen;
import dev.aurora.module.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws all enabled HUD modules; registered with Fabric's HUD element registry. */
public final class HudLayer {
	private HudLayer() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && !(mc.gui.screen() instanceof HudEditorScreen)
			&& !(Aurora.client().hideInDebug.on() && mc.getDebugOverlay().showDebugScreen())) {
			float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
			for (HudModule module : Aurora.modules().hud()) {
				if (module.isEnabled() && module.hasContent()) {
					draw(g, module, partialTick, false);
				}
			}
		}
		Notifications.render(g);
	}

	public static void draw(GuiGraphicsExtractor g, HudModule module, float partialTick, boolean editing) {
		module.layout(editing);
		module.ensurePlaced(g.guiWidth(), g.guiHeight());
		float x = module.x(g.guiWidth());
		float y = module.y(g.guiHeight());
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(module.scale(), module.scale());
		module.render(g, partialTick, editing);
		g.pose().popMatrix();
	}
}
