package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import net.minecraft.world.item.Items;

public final class MemoryHud extends TextHudModule {
	public MemoryHud() {
		super("memory", Items.ENDER_CHEST, false, 0, 0, 4, 198);
	}

	@Override
	protected String label() {
		return "MEM";
	}

	@Override
	protected String value(boolean editing) {
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
		long max = rt.maxMemory() / (1024 * 1024);
		return used * 100 / Math.max(1, max) + "% " + used + "/" + max + " MB";
	}
}
