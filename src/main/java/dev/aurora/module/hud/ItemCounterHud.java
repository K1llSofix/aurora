package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.ItemIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How many arrows, pearls, golden apples, totems, potions, rockets, blocks or food you carry, and
 * how many more of the item in your hand: no need to open the inventory to count. Only your own
 * inventory is counted. A count turns red when it runs low and flashes when it changes.
 */
public final class ItemCounterHud extends HudModule {
	private static final float ICON = 16f;
	private static final long FLASH_MS = 700;

	/** What one row counts, its default icon and from how many it is "running low". */
	private record Kind(String id, Predicate<ItemStack> matches, Item icon, int low, boolean on) {
	}

	private static final Kind[] KINDS = {
		new Kind("arrows", s -> s.is(Items.ARROW) || s.is(Items.TIPPED_ARROW) || s.is(Items.SPECTRAL_ARROW), Items.ARROW, 16, true),
		new Kind("pearls", s -> s.is(Items.ENDER_PEARL), Items.ENDER_PEARL, 2, true),
		new Kind("gapples", s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE), Items.GOLDEN_APPLE, 2, true),
		new Kind("totems", s -> s.is(Items.TOTEM_OF_UNDYING), Items.TOTEM_OF_UNDYING, 1, true),
		new Kind("potions", s -> s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION), Items.SPLASH_POTION, 2, false),
		new Kind("rockets", s -> s.is(Items.FIREWORK_ROCKET), Items.FIREWORK_ROCKET, 8, false),
		new Kind("blocks", s -> s.getItem() instanceof BlockItem, Items.COBBLESTONE, 32, false),
		new Kind("food", s -> s.has(DataComponents.FOOD), Items.BREAD, 4, false),
	};

	private record Row(String key, ItemStack icon, int count, boolean low) {
	}

	private final ModeSetting layout = add(new ModeSetting("layout", "horizontal", "horizontal", "vertical"));
	private final BooleanSetting held = add(new BooleanSetting("held", true));
	private final BooleanSetting[] kinds = new BooleanSetting[KINDS.length];
	private final BooleanSetting hideEmpty = add(new BooleanSetting("hide_empty", true));
	private final BooleanSetting lowWarning = add(new BooleanSetting("low_warning", true));

	private final List<Row> rows = new ArrayList<>();
	private final Map<String, Integer> lastCount = new HashMap<>();
	private final Map<String, Long> changedAt = new HashMap<>();
	private final Map<String, Boolean> rose = new HashMap<>();

	public ItemCounterHud() {
		// Just right of the hotbar.
		super("item_counter", Items.BUNDLE, false, 0.5f, 1f, 130, -2);
		for (int i = 0; i < KINDS.length; i++) {
			kinds[i] = add(new BooleanSetting(KINDS[i].id, KINDS[i].on));
		}
	}

	private void collect(boolean editing) {
		rows.clear();
		if (mc.player == null) {
			if (editing) {
				preview();
			}
			return;
		}
		Inventory inventory = mc.player.getInventory();
		int[] counts = new int[KINDS.length];
		ItemStack[] icons = new ItemStack[KINDS.length];
		ItemStack hand = mc.player.getMainHandItem();
		boolean handCounted = hand.isEmpty() || !hand.isStackable();
		int handTotal = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			// Armour does not count: a pumpkin on your head is not a block to build with.
			if (i >= Inventory.INVENTORY_SIZE && i != Inventory.SLOT_OFFHAND) {
				continue;
			}
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!hand.isEmpty() && ItemStack.isSameItem(stack, hand)) {
				handTotal += stack.getCount();
			}
			for (int k = 0; k < KINDS.length; k++) {
				if (kinds[k].on() && KINDS[k].matches.test(stack)) {
					counts[k] += stack.getCount();
					// The row shows the kind you have most of (a tipped arrow, a splash potion...).
					if (icons[k] == null || stack.getCount() > icons[k].getCount()) {
						icons[k] = stack;
					}
					if (ItemStack.isSameItem(stack, hand)) {
						handCounted = true;
					}
				}
			}
		}
		for (int k = 0; k < KINDS.length; k++) {
			if (!kinds[k].on() || counts[k] == 0 && hideEmpty.on()) {
				continue;
			}
			ItemStack icon = icons[k] != null ? icons[k].copyWithCount(1) : ItemIcons.stack(KINDS[k].icon);
			rows.add(new Row(KINDS[k].id, icon, counts[k], counts[k] <= KINDS[k].low));
		}
		// The item in your hand, when no row above counts it already.
		if (held.on() && !handCounted && handTotal > hand.getCount()) {
			rows.addFirst(new Row("held:" + hand.getItem().getDescriptionId(), hand.copyWithCount(1), handTotal,
				handTotal <= hand.getMaxStackSize() / 4));
		}
		if (rows.isEmpty() && editing) {
			preview();
		}
		long now = System.currentTimeMillis();
		for (Row row : rows) {
			Integer before = lastCount.put(row.key, row.count);
			if (before != null && before != row.count) {
				changedAt.put(row.key, now);
				rose.put(row.key, row.count > before);
			}
		}
	}

	/** Shown in the HUD editor, also on the title screen. */
	private void preview() {
		rows.add(new Row("arrows", ItemIcons.stack(Items.ARROW), 64, false));
		rows.add(new Row("pearls", ItemIcons.stack(Items.ENDER_PEARL), 16, false));
		rows.add(new Row("gapples", ItemIcons.stack(Items.GOLDEN_APPLE), 2, true));
	}

	@Override
	public boolean hasContent() {
		collect(false);
		return !rows.isEmpty();
	}

	private int color(Row row) {
		int base = row.low && lowWarning.on() ? 0xFFF87171 : HudDraw.textColor();
		Long at = changedAt.get(row.key);
		if (at == null) {
			return base;
		}
		float flash = 1f - (System.currentTimeMillis() - at) / (float) FLASH_MS;
		if (flash <= 0) {
			return base;
		}
		int to = Boolean.TRUE.equals(rose.get(row.key)) ? 0xFF4ADE80 : 0xFFFBBF24;
		return ColorUtil.lerp(base, to, flash * flash);
	}

	@Override
	public void layout(boolean editing) {
		collect(editing);
		float pad = HudDraw.padX();
		if (layout.is("horizontal")) {
			float w = 0;
			for (Row row : rows) {
				w += ICON + 2 + HudDraw.width(Integer.toString(row.count)) + 6;
			}
			width = Math.max(ICON, w - 6) + pad * 2;
			height = ICON + 4;
		} else {
			float textW = 0;
			for (Row row : rows) {
				textW = Math.max(textW, HudDraw.width(Integer.toString(row.count)));
			}
			width = ICON + 4 + textW + pad * 2;
			height = Math.max(1, rows.size()) * (ICON + 2) + 2;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.box(g, 0, 0, width, height);
		float pad = HudDraw.padX();
		if (layout.is("horizontal")) {
			float x = pad;
			for (Row row : rows) {
				g.item(row.icon, Math.round(x), 2);
				x += ICON + 2;
				x += HudDraw.text(g, Integer.toString(row.count), x, height / 2f, color(row)) + 6;
			}
			return;
		}
		boolean right = anchoredRight();
		float y = 2;
		for (Row row : rows) {
			String text = Integer.toString(row.count);
			if (right) {
				g.item(row.icon, Math.round(width - pad - ICON), Math.round(y));
				HudDraw.text(g, text, width - pad - ICON - 4 - HudDraw.width(text), y + ICON / 2f, color(row));
			} else {
				g.item(row.icon, Math.round(pad), Math.round(y));
				HudDraw.text(g, text, pad + ICON + 4, y + ICON / 2f, color(row));
			}
			y += ICON + 2;
		}
	}
}
