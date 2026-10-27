package dev.aurora.mixin;

import com.mojang.renderpearl.api.device.GpuSurface;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The window's GPU surface, and the flag that makes the game choose its present mode again. */
@Mixin(Minecraft.class)
public interface MinecraftSurfaceAccessor {
	@Accessor("windowSurface")
	GpuSurface aurora$windowSurface();

	@Accessor("windowSurfaceNeedsReconfiguring")
	void aurora$setWindowSurfaceNeedsReconfiguring(boolean value);
}
