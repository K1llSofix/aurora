package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.mixin.CooldownInstanceAccessor;
import dev.aurora.mixin.ItemCooldownsAccessor;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.ItemIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seconds left on your own item cooldowns: an ender pearl, a wind charge, chorus fruit, a shield
 * an axe knocked out of use. Vanilla only shades the hotbar slot; this shows the time.
 */
public final class CooldownsHud extends HudModule {
	private static final float ICON = 16f;
	private static final float BAR = 1.5f;

	private record Entry(ItemStack icon, float seconds, float fraction, int end) {
	}

	private final ModeSetting layout = add(new ModeSetting("layout", "vertical", "horizontal", "vertical"));
	private final BooleanSetting tenths = add(new BooleanSetting("tenths", true));
	private final BooleanSetting bar = add(new BooleanSetting("bar", true));

	private final List<Entry> entries = new ArrayList<>();

	public CooldownsHud() {
		// Left of the hotbar, mirroring the item counter.
		super("cooldowns", Items.WIND_CHARGE, false, 0.5f, 1f, -130, -2);
	}

	private void collect(float partialTick, boolean editing) {
		entries.clear();
		if (mc.player != null && mc.level != null) {
			ItemCooldowns cooldowns = mc.player.getCooldowns();
			ItemCooldownsAccessor access = (ItemCooldownsAccessor) cooldowns;
			float now = access.aurora$tickCount() + partialTick;
			float tickRate = mc.level.tickRateManager().tickrate();
			for (Map.Entry<Identifier, Object> e : access.aurora$cooldowns().entrySet()) {
				CooldownInstanceAccessor cooldown = (CooldownInstanceAccessor) e.getValue();
				float left = cooldown.aurora$endTime() - now;
				int duration = cooldown.aurora$endTime() - cooldown.aurora$startTime();
				if (left <= 0 || duration <= 0) {
					continue;
				}
				ItemStack icon = icon(e.getKey(), cooldowns);
				if (icon != null) {
					entries.add(new Entry(icon, left / tickRate, Math.min(1f, left / duration), cooldown.aurora$endTime()));
				}
			}
			// The one that ends first leads, so the order does not jump around.
			entries.sort(Comparator.comparingInt(Entry::end));
		}
		if (entries.isEmpty() && editing) {
			entries.add(new Entry(ItemIcons.stack(Items.ENDER_PEARL), 0.6f, 0.6f, 0));
			entries.add(new Entry(ItemIcons.stack(Items.WIND_CHARGE), 0.3f, 0.6f, 1));
			entries.add(new Entry(ItemIcons.stack(Items.SHIELD), 3.4f, 0.68f, 2));
		}
	}

	/** The item a cooldown group belongs to: the item of that name, or one of yours in that group. */
	private @Nullable ItemStack icon(Identifier group, ItemCooldowns cooldowns) {
		var item = BuiltInRegistries.ITEM.getOptional(group);
		if (item.isPresent() && item.get() != Items.AIR) {
			return ItemIcons.stack(item.get());
		}
		Inventory inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && group.equals(cooldowns.getCooldownGroup(stack))) {
				return stack.copyWithCount(1);
			}
		}
		return null;
	}

	private String time(Entry entry) {
		String number = tenths.on()
			? String.format(Locale.ROOT, "%.1f", entry.seconds)
			: Integer.toString((int) Math.ceil(entry.seconds));
		return I18n.get("aurora.unit.seconds", number);
	}

	@Override
	public boolean hasContent() {
		collect(0, false);
		return !entries.isEmpty();
	}

	@Override
	public void layout(boolean editing) {
		collect(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), editing);
		float pad = HudDraw.padX();
		float textW = 0;
		for (Entry entry : entries) {
			textW = Math.max(textW, HudDraw.width(time(entry)));
		}
		if (layout.is("horizontal")) {
			float cell = ICON + 3 + textW;
			width = Math.max(ICON, entries.size() * (cell + 6) - 6) + pad * 2;
			height = ICON + 4;
		} else {
			width = ICON + 4 + textW + pad * 2;
			height = Math.max(1, entries.size()) * (ICON + 2) + 2;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.box(g, 0, 0, width, height);
		float pad = HudDraw.padX();
		boolean horizontal = layout.is("horizontal");
		boolean right = !horizontal && anchoredRight();
		float textW = 0;
		for (Entry entry : entries) {
			textW = Math.max(textW, HudDraw.width(time(entry)));
		}
		float x = pad;
		float y = 2;
		for (Entry entry : entries) {
			String text = time(entry);
			float iconX = right ? width - pad - ICON : x;
			float textX = right ? iconX - 3 - HudDraw.width(text) : iconX + ICON + 3;
			g.item(entry.icon, Math.round(iconX), Math.round(y));
			// The last half second the number turns to the accent: the item is about to be ready.
			int color = entry.seconds < 0.5f ? ColorUtil.lerp(Theme.accent(), HudDraw.textColor(), entry.seconds * 2) : HudDraw.textColor();
			HudDraw.text(g, text, textX, y + ICON / 2f - (bar.on() ? 1 : 0), color);
			if (bar.on()) {
				float bx = right ? iconX - 3 - textW : textX;
				float by = y + ICON - 1.5f;
				Render2D.roundRect(g, bx, by, textW, BAR, BAR / 2f, ColorUtil.withAlpha(0xFFFFFF, 40));
				float fill = textW * entry.fraction;
				if (fill > 0.5f) {
					float fx = right ? bx + textW - fill : bx;
					Render2D.roundRect(g, fx, by, fill, BAR, BAR / 2f, Render2D.Paint.horizontal(fx, fill, Theme.accent(), Theme.accent2()));
				}
			}
			if (horizontal) {
				x += ICON + 3 + textW + 6;
			} else {
				y += ICON + 2;
			}
		}
	}
}
