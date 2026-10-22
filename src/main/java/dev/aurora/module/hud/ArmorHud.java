package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.ItemIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ArmorHud extends HudModule {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final float ICON = 16f;

	private final ModeSetting layout = add(new ModeSetting("layout", "vertical", "vertical", "horizontal"));
	private final ModeSetting durability = add(new ModeSetting("durability", "percent", "percent", "value", "none"));
	private final BooleanSetting hand = add(new BooleanSetting("hand", true));
	private final BooleanSetting colorize = add(new BooleanSetting("colorize", true));

	private final List<ItemStack> items = new ArrayList<>();

	public ArmorHud() {
		super("armor", Items.DIAMOND_CHESTPLATE, true, 1, 1, -80, -4);
	}

	private void collect(boolean editing) {
		items.clear();
		if (mc.player == null) {
			if (editing) {
				addPreview();
			}
			return;
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = mc.player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}
		if (hand.on() && !mc.player.getMainHandItem().isEmpty()) {
			items.add(mc.player.getMainHandItem());
		}
		if (items.isEmpty() && editing) {
			addPreview();
		}
	}

	/** Diamond armour shown in the HUD editor; works on the title screen too. */
	private void addPreview() {
		items.add(ItemIcons.stack(Items.DIAMOND_HELMET));
		items.add(ItemIcons.stack(Items.DIAMOND_CHESTPLATE));
		items.add(ItemIcons.stack(Items.DIAMOND_LEGGINGS));
		items.add(ItemIcons.stack(Items.DIAMOND_BOOTS));
	}

	@Override
	public boolean hasContent() {
		collect(false);
		return !items.isEmpty();
	}

	private String label(ItemStack stack) {
		if (durability.is("none") || !stack.isDamageableItem()) {
			return stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "";
		}
		int left = stack.getMaxDamage() - stack.getDamageValue();
		if (durability.is("value")) {
			return Integer.toString(left);
		}
		return Math.round(left * 100f / stack.getMaxDamage()) + "%";
	}

	private int labelColor(ItemStack stack) {
		if (!colorize.on() || !stack.isDamageableItem()) {
			return HudDraw.textColor();
		}
		float f = 1f - stack.getDamageValue() / (float) stack.getMaxDamage();
		return f > 0.5f ? ColorUtil.lerp(0xFFFBBF24, 0xFF4ADE80, (f - 0.5f) * 2f) : ColorUtil.lerp(0xFFF87171, 0xFFFBBF24, f * 2f);
	}

	@Override
	public void layout(boolean editing) {
		collect(editing);
		float pad = HudDraw.padX();
		if (layout.is("horizontal")) {
			float w = 0;
			for (ItemStack stack : items) {
				String l = label(stack);
				w += ICON + (l.isEmpty() ? 0 : 2 + HudDraw.width(l)) + 6;
			}
			width = Math.max(ICON, w - 6) + pad * 2;
			height = ICON + 4;
		} else {
			float textW = 0;
			for (ItemStack stack : items) {
				textW = Math.max(textW, HudDraw.width(label(stack)));
			}
			width = ICON + (textW > 0 ? 4 + textW : 0) + pad * 2;
			height = Math.max(1, items.size()) * (ICON + 2) + 2;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.box(g, 0, 0, width, height);
		float pad = HudDraw.padX();
		boolean right = anchoredRight();
		if (layout.is("horizontal")) {
			float x = pad;
			for (ItemStack stack : items) {
				g.item(stack, Math.round(x), 2);
				x += ICON;
				String l = label(stack);
				if (!l.isEmpty()) {
					x += 2;
					x += HudDraw.text(g, l, x, height / 2f, labelColor(stack));
				}
				x += 6;
			}
			return;
		}
		float y = 2;
		for (ItemStack stack : items) {
			String l = label(stack);
			float lw = HudDraw.width(l);
			if (right) {
				// Text on the left, icon flush with the right edge.
				g.item(stack, Math.round(width - pad - ICON), Math.round(y));
				if (!l.isEmpty()) {
					HudDraw.text(g, l, width - pad - ICON - 4 - lw, y + ICON / 2f, labelColor(stack));
				}
			} else {
				g.item(stack, Math.round(pad), Math.round(y));
				if (!l.isEmpty()) {
					HudDraw.text(g, l, pad + ICON + 4, y + ICON / 2f, labelColor(stack));
				}
			}
			y += ICON + 2;
		}
	}
}
