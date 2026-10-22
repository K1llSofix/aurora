package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aurora.Aurora;
import dev.aurora.module.visual.WeatherModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class LevelMixin {
	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float aurora$clearRain(float level) {
		return clear() ? 0f : level;
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float aurora$clearThunder(float level) {
		return clear() ? 0f : level;
	}

	private boolean clear() {
		if (!((Object) this instanceof ClientLevel) || Aurora.modules() == null) {
			return false;
		}
		WeatherModule weather = Aurora.modules().get(WeatherModule.class);
		return weather != null && weather.isEnabled();
	}
}
