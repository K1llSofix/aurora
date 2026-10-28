package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.ItemIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What you just picked up from the ground, "+16 Cobblestone", sliding in at the side and fading
 * away after a few seconds. More of the same item adds up in one row.
 */
public final class PickupsHud extends HudModule {
	private static final float ICON = 16f;
	private static final long SLIDE_MS = 220;
	private static final long FADE_MS = 500;

	private static final class Entry {
		final ItemStack icon;
		int count;
		long firstAt;
		long lastAt;

		Entry(ItemStack icon, int count, long now) {
			this.icon = icon;
			this.count = count;
			this.firstAt = now;
			this.lastAt = now;
		}
	}

	private final NumberSetting duration = add(new NumberSetting("duration", 4, 2, 10, 1)
		.format(v -> I18n.get("aurora.unit.seconds", (int) v)));
	private final NumberSetting rows = add(new NumberSetting("rows", 5, 1, 10, 1));
	private final BooleanSetting names = add(new BooleanSetting("names", true));

	private static @Nullable PickupsHud instance;
	private final List<Entry> entries = new ArrayList<>();
	private final List<Entry> preview = new ArrayList<>();

	public PickupsHud() {
		super("pickups", Items.HOPPER, false, 1f, 0.68f, -4, 0);
		instance = this;
	}

	/** Called on the client thread when the server says you picked something up. */
	public static void onPickup(ItemStack stack, int count) {
		PickupsHud hud = instance;
		if (hud == null || !hud.isEnabled() || stack.isEmpty() || count <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		hud.prune(now);
		for (Entry entry : hud.entries) {
			if (ItemStack.isSameItemSameComponents(entry.icon, stack)) {
				entry.count += count;
				entry.lastAt = now;
				// The row that just grew moves to the top.
				hud.entries.remove(entry);
				hud.entries.addFirst(entry);
				return;
			}
		}
		hud.entries.addFirst(new Entry(stack.copyWithCount(1), count, now));
		while (hud.entries.size() > hud.rows.intValue()) {
			hud.entries.removeLast();
		}
	}

	private long lifetime() {
		return duration.intValue() * 1000L;
	}

	private void prune(long now) {
		entries.removeIf(e -> now - e.lastAt > lifetime());
	}

	private List<Entry> shown(boolean editing) {
		prune(System.currentTimeMillis());
		if (!entries.isEmpty() || !editing) {
			return entries;
		}
		if (preview.isEmpty()) {
			long now = System.currentTimeMillis() - 10_000;
			preview.add(new Entry(ItemIcons.stack(Items.COBBLESTONE), 32, now));
			preview.add(new Entry(ItemIcons.stack(Items.IRON_INGOT), 3, now));
			preview.add(new Entry(ItemIcons.stack(Items.ARROW), 1, now));
		}
		return preview;
	}

	@Override
	public boolean hasContent() {
		return !shown(false).isEmpty();
	}

	private String count(Entry entry) {
		return "+" + entry.count;
	}

	private String name(Entry entry) {
		return names.on() ? entry.icon.getHoverName().getString() : "";
	}

	@Override
	public void layout(boolean editing) {
		List<Entry> list = shown(editing);
		float textW = 0;
		for (Entry entry : list) {
			String name = name(entry);
			textW = Math.max(textW, HudDraw.width(count(entry)) + (name.isEmpty() ? 0 : 4 + HudDraw.width(name)));
		}
		width = ICON + 4 + textW + HudDraw.padX() * 2;
		height = Math.max(1, list.size()) * (ICON + 2) + 2;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		List<Entry> list = shown(editing);
		boolean right = anchoredRight();
		float pad = HudDraw.padX();
		long now = System.currentTimeMillis();
		float y = 2;
		for (Entry entry : list) {
			float in = Easing.OUT_CUBIC.clamped((now - entry.firstAt) / (float) SLIDE_MS);
			long left = lifetime() - (now - entry.lastAt);
			float alpha = Math.max(0, Math.min(1, left / (float) FADE_MS)) * in;
			if (alpha <= 0.01f) {
				y += ICON + 2;
				continue;
			}
			String count = count(entry);
			String name = name(entry);
			float rowW = ICON + 4 + HudDraw.width(count) + (name.isEmpty() ? 0 : 4 + HudDraw.width(name)) + pad * 2;
			// New rows slide in from the edge the element is attached to.
			float slide = (1 - in) * 14 * (right ? 1 : -1);
			float rx = (right ? width - rowW : 0) + slide;
			g.pose().pushMatrix();
			g.pose().translate(rx, y - 1);
			Render2D.globalAlpha = alpha;
			HudDraw.box(g, 0, 0, rowW, ICON + 2);
			Render2D.globalAlpha = 1f;
			g.pose().popMatrix();
			float x = rx + pad;
			// Items cannot fade, so the icon leaves a little before the text.
			if (alpha > 0.35f) {
				g.item(entry.icon, Math.round(x), Math.round(y));
			}
			x += ICON + 4;
			x += HudDraw.text(g, count, x, y + ICON / 2f, ColorUtil.fade(Theme.accent(), alpha));
			if (!name.isEmpty()) {
				HudDraw.text(g, name, x + 4, y + ICON / 2f, ColorUtil.fade(HudDraw.textColor(), alpha));
			}
			y += ICON + 2;
		}
	}
}
