package dev.aurora.module.hud;

import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Your hidden saturation as a golden line under the hunger bar, and, while you hold food, the
 * shanks it would fill blinking softly. Only your own hunger is shown.
 */
public final class SaturationModule extends Module {
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
	private static final Identifier HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");
	private static final int GOLD = 0xFFFFC94A;
	private static final int GOLD_NEW = 0xFFFFF0B5;

	private final BooleanSetting saturation = add(new BooleanSetting("saturation", true));
	private final BooleanSetting preview = add(new BooleanSetting("preview", true));

	private static SaturationModule instance;

	public SaturationModule() {
		super("saturation", Category.HUD, Items.COOKED_BEEF, true);
		instance = this;
	}

	public static @Nullable SaturationModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Called right after the vanilla hunger bar; {@code xRight} is its right edge. */
	public void render(GuiGraphicsExtractor g, Player player, int yLineBase, int xRight) {
		FoodData data = player.getFoodData();
		int food = data.getFoodLevel();
		float sat = data.getSaturationLevel();
		FoodProperties held = preview.on() ? heldFood(player) : null;
		int newFood = held == null ? food : Math.min(20, food + held.nutrition());
		float newSat = held == null ? sat : Math.min(newFood, sat + held.saturation());
		// A slow pulse, like AppleSkin: what eating would add, not what you have.
		float pulse = 0.35f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 160.0);

		if (held != null && newFood > food) {
			boolean hunger = player.hasEffect(MobEffects.HUNGER);
			for (int i = 0; i < 10; i++) {
				int xo = xRight - i * 8 - 9;
				boolean fullNow = i * 2 + 1 < food;
				boolean halfNow = i * 2 + 1 == food;
				if (i * 2 + 1 < newFood && !fullNow) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? FULL_HUNGER : FULL, xo, yLineBase, 9, 9, pulse);
				} else if (i * 2 + 1 == newFood && !fullNow && !halfNow) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? HALF_HUNGER : HALF, xo, yLineBase, 9, 9, pulse);
				}
			}
		}

		if (!saturation.on() || newSat <= 0) {
			return;
		}
		// One segment under each shank, filling from the right like the bar itself.
		float y = yLineBase + 9;
		for (int i = 0; i < 10; i++) {
			int xo = xRight - i * 8 - 9;
			float have = segment(sat, i);
			float will = segment(newSat, i);
			if (will > have) {
				Render2D.rect(g, xo + 1 + 7 * (1 - will), y, 7 * (will - have), 1, ColorUtil.withAlpha(GOLD_NEW, Math.round(255 * (pulse + 0.25f))));
			}
			if (have > 0) {
				Render2D.rect(g, xo + 1 + 7 * (1 - have), y, 7 * have, 1, GOLD);
			}
		}
	}

	/** How much of shank {@code i} the saturation covers, 0 to 1. */
	private static float segment(float saturation, int i) {
		return Math.max(0, Math.min(1, (saturation - i * 2) / 2f));
	}

	private static @Nullable FoodProperties heldFood(Player player) {
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null && player.canEat(food.canAlwaysEat())) {
				return food;
			}
		}
		return null;
	}
}
