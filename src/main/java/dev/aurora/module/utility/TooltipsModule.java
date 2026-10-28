package dev.aurora.module.utility;

import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Smarter item tooltips: what is inside a shulker box (or any item that carries a container) as a
 * grid of slots, a picture of a filled map, how much a food restores and how worn a tool is.
 */
public final class TooltipsModule extends Module {
	private static final int SLOT = 18;
	private static final int MAP_SIZE = 80;

	private final BooleanSetting containers = add(new BooleanSetting("containers", true));
	private final BooleanSetting maps = add(new BooleanSetting("maps", true));
	private final BooleanSetting food = add(new BooleanSetting("food", true));
	private final BooleanSetting durability = add(new BooleanSetting("durability", true));

	private static @Nullable TooltipsModule instance;

	public TooltipsModule() {
		super("tooltips", Category.UTILITY, Items.SHULKER_BOX, true);
		instance = this;
	}

	private static @Nullable TooltipsModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** The contents of a container item, shown as slots. */
	public record ContainerPreview(List<ItemStack> items, @Nullable DyeColor color) implements TooltipComponent {
	}

	/** A filled map, shown as a picture. */
	public record MapPreview(MapId id) implements TooltipComponent {
	}

	/** Whether container items show their slots (and so drop the vanilla list of the first five). */
	public static boolean showsContainers() {
		TooltipsModule tooltips = active();
		return tooltips != null && tooltips.containers.on();
	}

	/** The picture for an item tooltip, when vanilla has none. */
	public static Optional<TooltipComponent> image(ItemStack stack) {
		TooltipsModule tooltips = active();
		if (tooltips == null) {
			return Optional.empty();
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (tooltips.containers.on() && contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent()) {
			List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
			Block block = Block.byItem(stack.getItem());
			// A shulker box always shows its three rows; other containers as many rows as they use.
			int slots = block instanceof ShulkerBoxBlock ? 27 : Math.min(54, (items.size() + 8) / 9 * 9);
			while (items.size() < slots) {
				items.add(ItemStack.EMPTY);
			}
			DyeColor color = block instanceof ShulkerBoxBlock shulker ? shulker.getColor() : null;
			return Optional.of(new ContainerPreview(List.copyOf(items.subList(0, slots)), color));
		}
		MapId map = stack.get(DataComponents.MAP_ID);
		if (tooltips.maps.on() && map != null && mc.level != null && MapItem.getSavedData(map, mc.level) != null) {
			return Optional.of(new MapPreview(map));
		}
		return Optional.empty();
	}

	/** Food values and durability, added under the vanilla lines. */
	public static void addLines(ItemStack stack, TooltipFlag flag, List<Component> lines) {
		TooltipsModule tooltips = active();
		if (tooltips == null) {
			return;
		}
		FoodProperties properties = stack.get(DataComponents.FOOD);
		if (tooltips.food.on() && properties != null) {
			lines.add(Component.translatable("aurora.tooltips.food",
				Component.literal("+" + properties.nutrition()).withStyle(ChatFormatting.GOLD),
				Component.literal("+" + String.format(Locale.ROOT, "%.1f", properties.saturation())).withStyle(ChatFormatting.YELLOW)
			).withStyle(ChatFormatting.GRAY));
		}
		// Advanced tooltips (F3+H) already show it.
		if (tooltips.durability.on() && stack.isDamaged() && !flag.isAdvanced()) {
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			float part = left / (float) max;
			ChatFormatting color = part > 0.5f ? ChatFormatting.GREEN : part > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
			lines.add(Component.translatable("item.durability", Component.literal(Integer.toString(left)).withStyle(color), max)
				.withStyle(ChatFormatting.GRAY));
		}
	}

	/** Turns the tooltip data above into what is drawn; registered with Fabric's tooltip callback. */
	public static @Nullable ClientTooltipComponent client(TooltipComponent data) {
		if (data instanceof ContainerPreview preview) {
			return new ContainerTooltip(preview);
		}
		if (data instanceof MapPreview preview) {
			return new MapTooltip(preview.id());
		}
		return null;
	}

	private record ContainerTooltip(ContainerPreview preview) implements ClientTooltipComponent {
		private int rows() {
			return Math.max(1, (preview.items().size() + 8) / 9);
		}

		@Override
		public int getHeight(Font font) {
			return rows() * SLOT + 6;
		}

		@Override
		public int getWidth(Font font) {
			return 9 * SLOT + 2;
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			int tint = preview.color() == null ? 0xFF8F96A8 : 0xFF000000 | preview.color().getTextureDiffuseColor();
			float top = y + 2;
			float height = rows() * SLOT + 1;
			Render2D.roundRect(g, x, top, 9 * SLOT + 1, height, 4, ColorUtil.withAlpha(ColorUtil.lerp(0x0B0D12, tint & 0xFFFFFF, 0.18f), 200));
			Render2D.roundOutline(g, x, top, 9 * SLOT + 1, height, 4, 0.6f, ColorUtil.withAlpha(tint, 110));
			List<ItemStack> items = preview.items();
			for (int i = 0; i < items.size(); i++) {
				int sx = x + 1 + (i % 9) * SLOT;
				int sy = y + 3 + (i / 9) * SLOT;
				Render2D.roundRect(g, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 2, 0x22FFFFFF);
				ItemStack stack = items.get(i);
				if (!stack.isEmpty()) {
					g.item(stack, sx + 1, sy + 1);
					g.itemDecorations(font, stack, sx + 1, sy + 1);
				}
			}
		}
	}

	private static final class MapTooltip implements ClientTooltipComponent {
		private final MapId id;
		private final MapRenderState state = new MapRenderState();

		MapTooltip(MapId id) {
			this.id = id;
		}

		@Override
		public int getHeight(Font font) {
			return MAP_SIZE + 6;
		}

		@Override
		public int getWidth(Font font) {
			return MAP_SIZE + 2;
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			MapItemSavedData data = mc.level == null ? null : MapItem.getSavedData(id, mc.level);
			if (data == null) {
				return;
			}
			Render2D.roundRect(g, x, y + 2, MAP_SIZE + 2, MAP_SIZE + 2, 3, 0xFFD9C79E);
			g.pose().pushMatrix();
			g.pose().translate(x + 1, y + 3);
			g.pose().scale(MAP_SIZE / 128f, MAP_SIZE / 128f);
			mc.getMapRenderer().extractRenderState(id, data, state);
			g.map(state);
			g.pose().popMatrix();
		}
	}
}
