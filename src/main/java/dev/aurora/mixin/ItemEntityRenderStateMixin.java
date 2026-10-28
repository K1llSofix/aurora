package dev.aurora.mixin;

import dev.aurora.module.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Whether a dropped item lies on the ground or floats, for item physics. */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsModule.State {
	@Unique
	private boolean aurora$onGround;

	@Unique
	private boolean aurora$inFluid;

	@Override
	public boolean aurora$onGround() {
		return aurora$onGround;
	}

	@Override
	public boolean aurora$inFluid() {
		return aurora$inFluid;
	}

	@Override
	public void aurora$setPhysics(boolean onGround, boolean inFluid) {
		aurora$onGround = onGround;
		aurora$inFluid = inFluid;
	}
}
