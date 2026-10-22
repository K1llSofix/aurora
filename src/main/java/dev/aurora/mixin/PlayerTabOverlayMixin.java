package dev.aurora.mixin;

import dev.aurora.Aurora;
import dev.aurora.module.hud.TabListModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Draws Aurora's player list instead of vanilla's (vanilla still decides when it is shown). */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin implements TabListModule.Source {
	@Shadow
	private @Nullable Component header;

	@Shadow
	private @Nullable Component footer;

	@Shadow
	private List<PlayerInfo> getPlayerInfos() {
		throw new AssertionError();
	}

	@Override
	public List<PlayerInfo> aurora$players() {
		return getPlayerInfos();
	}

	@Override
	public @Nullable Component aurora$header() {
		return header;
	}

	@Override
	public @Nullable Component aurora$footer() {
		return footer;
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void aurora$render(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, @Nullable Objective displayObjective,
							   CallbackInfo ci) {
		TabListModule tab = Aurora.modules().get(TabListModule.class);
		if (tab != null && tab.isEnabled()) {
			tab.render((PlayerTabOverlay) (Object) this, this, graphics, screenWidth, scoreboard, displayObjective);
			ci.cancel();
		}
	}
}
