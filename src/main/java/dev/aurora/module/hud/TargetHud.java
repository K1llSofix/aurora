package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.EntityPortrait;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.CombatTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Shows who you are fighting: face, animated health with a trailing damage bar, armour. */
public final class TargetHud extends HudModule {
	private static final float W = 132;
	private static final float H = 42;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final NumberSetting keep = add(new NumberSetting("keep", 4, 1, 15, 1).suffix("s"));
	private final BooleanSetting onLook = add(new BooleanSetting("on_look", true));
	private final BooleanSetting armor = add(new BooleanSetting("armor", true));
	private final BooleanSetting winLose = add(new BooleanSetting("win_lose", true));

	private final Animation health = new Animation(1f, 12f);
	private final Animation ghost = new Animation(1f, 2.6f);
	private final Animation visibility = new Animation(0f, 14f);
	private @Nullable LivingEntity shown;
	private @Nullable LivingEntity lastRendered;
	private float lastFraction = 1f;
	private long ghostHoldUntil;

	public TargetHud() {
		super("target", Items.TARGET, true, 0.5f, 0.5f, 80, 30);
	}

	private @Nullable LivingEntity currentTarget() {
		LivingEntity t = CombatTracker.target(keep.intValue() * 1000L);
		if (t == null && onLook.on() && mc.crosshairPickEntity instanceof LivingEntity looked && mc.player != null
			&& looked.distanceTo(mc.player) < 8) {
			t = looked;
		}
		return t;
	}

	@Override
	public boolean hasContent() {
		LivingEntity t = currentTarget();
		if (t != null) {
			shown = t;
		}
		visibility.target(t != null);
		return visibility.get() > 0.01f && shown != null;
	}

	@Override
	public void layout(boolean editing) {
		width = W;
		height = H;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		LivingEntity target = editing ? (shown != null && currentTarget() != null ? shown : mc.player) : shown;
		if (target == null) {
			return;
		}
		float vis = editing ? 1f : visibility.peek();
		if (vis < 0.999f) {
			// Pop in: scale from 92% around the centre while fading.
			float s = 0.92f + 0.08f * vis;
			g.pose().translate(W / 2f * (1 - s), H / 2f * (1 - s));
			g.pose().scale(s, s);
		}

		float max = Math.max(1f, target.getMaxHealth());
		float hp = Math.max(0f, target.getHealth());
		float fraction = Math.min(1f, hp / max);
		if (target != lastRendered) {
			lastRendered = target;
			health.snap(fraction);
			ghost.snap(fraction);
			lastFraction = fraction;
		}
		if (fraction < lastFraction) {
			ghostHoldUntil = System.currentTimeMillis() + 350;
		}
		lastFraction = fraction;
		health.target(fraction);
		if (System.currentTimeMillis() > ghostHoldUntil || ghost.peek() < fraction) {
			ghost.target(fraction);
		}
		float hpAnim = health.get();
		float ghostAnim = Math.max(ghost.get(), hpAnim);

		int alpha255 = Math.round(255 * vis);
		// Panel
		if (!HudDraw.minimal()) {
			Render2D.shadow(g, 0, 0, W, H, 7, 10, ColorUtil.withAlpha(0x000000, Math.round(90 * vis)));
			Render2D.roundRect(g, 0, 0, W, H, 7, Render2D.Paint.vertical(0, H,
				ColorUtil.withAlpha(0x151821, Math.round(232 * vis)), ColorUtil.withAlpha(0x0D0F14, Math.round(240 * vis))));
			Render2D.roundOutline(g, 0, 0, W, H, 7, 0.6f, ColorUtil.withAlpha(0xFFFFFF, Math.round(22 * vis)));
		}

		// Face
		float face = 30;
		float fx = 6;
		float fy = (H - face) / 2f;
		float hurt = target.hurtTime > 0 ? target.hurtTime / 10f : 0f;
		Render2D.roundRect(g, fx - 1, fy - 1, face + 2, face + 2, 5,
			Render2D.Paint.diagonal(fx, fy, face, face, ColorUtil.withAlpha(Theme.accent(), alpha255), ColorUtil.withAlpha(Theme.accent2(), alpha255)));
		if (target instanceof AbstractClientPlayer player) {
			int tint = ColorUtil.lerp(0xFFFFFFFF, 0xFFFF6A6A, hurt);
			PlayerFaceExtractor.extractRenderState(g, player.getSkin(), Math.round(fx), Math.round(fy), Math.round(face), ColorUtil.withAlpha(tint, alpha255));
		} else {
			// Mobs: a live 3D portrait of the head on a soft backdrop.
			Render2D.roundRect(g, fx, fy, face, face, 4, Render2D.Paint.vertical(fy, face,
				ColorUtil.withAlpha(0x262B36, alpha255), ColorUtil.withAlpha(0x12151C, alpha255)));
			if (vis > 0.6f) {
				EntityPortrait.head(g, target, fx, fy, face, partialTick);
			}
		}

		// Name + win/lose chip
		float tx = fx + face + 7;
		float right = W - 7;
		String name = target.getName().getString();
		String chip = null;
		int chipColor = 0;
		if (winLose.on() && mc.player != null && target != mc.player) {
			float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
			float theirs = hp + target.getAbsorptionAmount();
			chip = mine >= theirs ? "W" : "L";
			chipColor = mine >= theirs ? Theme.SUCCESS : Theme.DANGER;
		}
		float nameMax = right - tx - (chip != null ? 14 : 0);
		FontRenderer.SEMIBOLD.drawMiddle(g, FontRenderer.SEMIBOLD.ellipsize(name, 8.5f, nameMax), tx, 11, 8.5f,
			ColorUtil.withAlpha(0xF1F3F8, alpha255));
		if (chip != null) {
			Render2D.roundRect(g, right - 11, 5.5f, 11, 11, 3, ColorUtil.fade(chipColor, 0.22f * vis));
			FontRenderer.BOLD.drawCentered(g, chip, right - 5.5f, 11, 7, ColorUtil.fade(chipColor, vis));
		}

		// Health bar with a trailing "ghost" that shows recent damage.
		float barY = 19;
		float barW = right - tx;
		float barH = 5;
		Render2D.roundRect(g, tx, barY, barW, barH, 2.5f, ColorUtil.withAlpha(0xFFFFFF, Math.round(18 * vis)));
		if (ghostAnim > hpAnim + 0.002f) {
			Render2D.roundRect(g, tx, barY, barW * ghostAnim, barH, 2.5f, ColorUtil.withAlpha(0xFFE7E9EE, Math.round(150 * vis)));
		}
		int c1 = healthColor(hpAnim);
		int c2 = ColorUtil.shade(c1, 0.25f);
		if (hpAnim > 0.001f) {
			Render2D.roundRect(g, tx, barY, Math.max(barH, barW * hpAnim), barH, 2.5f,
				Render2D.Paint.horizontal(tx, barW, ColorUtil.fade(c1, vis), ColorUtil.fade(c2, vis)));
		}
		float absorption = target.getAbsorptionAmount();
		if (absorption > 0) {
			float af = Math.min(1f, absorption / max);
			Render2D.roundRect(g, tx, barY + barH - 1.5f, barW * af, 1.5f, 0.75f, ColorUtil.fade(0xFFFBBF24, vis));
		}

		// Numbers + armour
		String hpText = String.format(Locale.ROOT, "%.1f", hp + absorption) + " HP";
		float rowY = 33;
		FontRenderer.MEDIUM.drawMiddle(g, hpText, tx, rowY, 7, ColorUtil.withAlpha(0xA3ABBB, alpha255));
		if (armor.on()) {
			float ax = right;
			for (EquipmentSlot slot : ARMOR) {
				ItemStack stack = target.getItemBySlot(slot);
				if (stack.isEmpty()) {
					continue;
				}
				ax -= 10;
				g.pose().pushMatrix();
				g.pose().translate(ax, rowY - 5);
				g.pose().scale(0.625f, 0.625f);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}
		}
	}

	private static int healthColor(float f) {
		if (f > 0.5f) {
			return ColorUtil.lerp(0xFFFBBF24, 0xFF34D399, (f - 0.5f) * 2f);
		}
		return ColorUtil.lerp(0xFFF43F5E, 0xFFFBBF24, f * 2f);
	}
}
