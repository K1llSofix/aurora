package dev.aurora.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Module;
import dev.aurora.setting.KeySetting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.KeyNames;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One line of the Binds page: icon, name and the key, with a warning when the key is shared. */
public class BindRow extends SettingRow {
	/** Something that can be bound: a module toggle or one of Aurora's own key mappings. */
	public interface Bind {
		String name();

		@Nullable ItemStack icon();

		InputConstants.Key key();

		void set(InputConstants.Key key);

		void reset();

		boolean allowsMouse();
	}

	public static Bind of(Module module) {
		return new Bind() {
			@Override
			public String name() {
				return module.name();
			}

			@Override
			public ItemStack icon() {
				return module.icon();
			}

			@Override
			public InputConstants.Key key() {
				int code = module.keybind().get();
				return code == KeySetting.NONE ? InputConstants.UNKNOWN : InputConstants.Type.KEYBOARD.getOrCreate(code);
			}

			@Override
			public void set(InputConstants.Key key) {
				module.keybind().set(key.equals(InputConstants.UNKNOWN) || key.getType() != InputConstants.Type.KEYBOARD
					? KeySetting.NONE : key.getValue());
				Aurora.config().markDirty();
			}

			@Override
			public void reset() {
				module.keybind().reset();
				Aurora.config().markDirty();
			}

			@Override
			public boolean allowsMouse() {
				return false;
			}
		};
	}

	public static Bind of(KeyMapping mapping) {
		return new Bind() {
			@Override
			public String name() {
				return I18n.get(mapping.getName());
			}

			@Override
			public @Nullable ItemStack icon() {
				return null;
			}

			@Override
			public InputConstants.Key key() {
				return KeyMappingHelper.getBoundKeyOf(mapping);
			}

			@Override
			public void set(InputConstants.Key key) {
				mapping.setKey(key);
				KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}

			@Override
			public void reset() {
				set(mapping.getDefaultKey());
			}

			@Override
			public boolean allowsMouse() {
				return true;
			}
		};
	}

	private final Bind bind;
	private final List<Bind> all;
	protected boolean listening;
	private final Animation pulse = new Animation(0, 10);
	protected float bx, by, bw, bh;

	public BindRow(Bind bind, List<Bind> all) {
		super(bind.name(), () -> {
		});
		this.bind = bind;
		this.all = all;
	}

	public Bind bind() {
		return bind;
	}

	private String keyName() {
		InputConstants.Key key = bind.key();
		if (key.equals(InputConstants.UNKNOWN)) {
			return I18n.get("aurora.ui.none");
		}
		return KeyNames.of(key);
	}

	/** Names of everything else that uses the same key, vanilla controls included. */
	private List<String> conflicts() {
		List<String> names = new ArrayList<>();
		InputConstants.Key key = bind.key();
		if (key.equals(InputConstants.UNKNOWN)) {
			return names;
		}
		for (Bind other : all) {
			if (other != bind && key.equals(other.key())) {
				names.add(other.name());
			}
		}
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			// Aurora's own mappings are already listed; debug keys only work together with F3.
			if (mapping.getCategory() == Aurora.KEY_CATEGORY || mapping.getCategory() == KeyMapping.Category.DEBUG) {
				continue;
			}
			if (key.equals(KeyMappingHelper.getBoundKeyOf(mapping))) {
				names.add(I18n.get(mapping.getName()));
			}
		}
		return names;
	}

	/** Space kept free right of the key button (for extra controls in subclasses). */
	protected float rightInset() {
		return 0;
	}

	protected void renderIcon(GuiGraphicsExtractor g, float iconX, float cy) {
		ItemStack icon = bind.icon();
		if (icon != null && Ui.alpha > 0.55f) {
			g.pose().pushMatrix();
			g.pose().translate(iconX, cy - 6);
			g.pose().scale(0.75f, 0.75f);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
		} else if (icon == null) {
			Render2D.roundRect(g, iconX, cy - 6, 12, 12, 3.5f,
				Render2D.Paint.diagonal(iconX, cy - 6, 12, 12, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
			Ui.spark(g, iconX + 6, cy, 4.2f, 1.1f, Ui.c(0xFFFFFFFF));
		}
	}

	/** The name between the icon and the key button. */
	protected void renderLabel(GuiGraphicsExtractor g, int mx, int my, float left, float right) {
		String text = FontRenderer.MEDIUM.ellipsize(label, LABEL_SIZE, right - left);
		FontRenderer.MEDIUM.drawMiddle(g, text, left, y + HEIGHT / 2f, LABEL_SIZE, Ui.c(Theme.TEXT));
	}

	/** Drawn last, e.g. buttons in the space reserved by {@link #rightInset()}. */
	protected void renderExtra(GuiGraphicsExtractor g, int mx, int my) {
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
		float hv = hover.target(Ui.inside(mx, my, x, y, w, HEIGHT) && !blocked).get();
		if (hv > 0.01f) {
			Render2D.roundRect(g, x + 2, y + 2, w - 4, HEIGHT - 4, 6, Ui.c(ColorUtil.withAlpha(0xFFFFFF, Math.round(7 * hv))));
		}
		List<String> conflicts = conflicts();
		boolean conflict = !conflicts.isEmpty();
		String text = listening ? I18n.get("aurora.ui.press_key") : keyName();
		bw = Math.max(64, FontRenderer.SEMIBOLD.width(text, 7.5f) + 20);
		bh = 18;
		bx = x + w - 12 - rightInset() - bw;
		by = y + (HEIGHT - bh) / 2f;

		float iconX = x + 12;
		renderIcon(g, iconX, y + HEIGHT / 2f);
		renderLabel(g, mx, my, iconX + 20, bx - (conflict ? 22 : 10));

		float p = pulse.target(listening).get();
		boolean hov = Ui.inside(mx, my, bx, by, bw, bh);
		if (p > 0.01f) {
			float wave = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
			Render2D.glow(g, bx, by, bw, bh, 6, 6, Ui.c(ColorUtil.fade(Theme.accent(), (0.15f + 0.2f * wave) * p)));
		}
		Render2D.roundRect(g, bx, by, bw, bh, 6, Ui.c(hov ? 0xFF161A22 : Theme.INPUT));
		int border = conflict ? ColorUtil.fade(Theme.WARNING, 0.85f) : (hov ? Theme.BORDER_STRONG : Theme.BORDER);
		Render2D.roundOutline(g, bx, by, bw, bh, 6, 0.8f, Ui.c(ColorUtil.lerp(border, Theme.accent(), p)));
		boolean unbound = bind.key().equals(InputConstants.UNKNOWN);
		int textColor = listening ? Theme.accent() : (unbound ? Theme.TEXT_MUTED : (conflict ? Theme.WARNING : Theme.TEXT));
		FontRenderer.SEMIBOLD.drawCentered(g, text, bx + bw / 2f, by + bh / 2f, 7.5f, Ui.c(textColor));

		if (conflict && !listening) {
			float ix = bx - 12;
			float iy = by + bh / 2f;
			Render2D.circle(g, ix, iy, 5, Ui.c(ColorUtil.fade(Theme.WARNING, 0.2f)));
			FontRenderer.BOLD.drawCentered(g, "!", ix, iy, 7, Ui.c(Theme.WARNING));
			if (Ui.inside(mx, my, ix - 6, iy - 6, 12, 12) || hov) {
				g.setTooltipForNextFrame(Component.translatable("aurora.ui.binds.conflict", String.join(", ", conflicts)), mx, my);
			}
		}
		renderExtra(g, mx, my);
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (listening) {
			// While waiting for a key, a click on the key button binds that mouse button
			// (Aurora's own mappings only); a click anywhere else cancels.
			listening = false;
			if (bind.allowsMouse() && Ui.inside(mx, my, bx, by, bw, bh)) {
				bind.set(InputConstants.Type.MOUSE.getOrCreate(button));
			}
			return true;
		}
		if (!Ui.inside(mx, my, x, y, w, HEIGHT)) {
			return false;
		}
		if (button == 3) {
			bind.set(InputConstants.UNKNOWN);
			return true;
		}
		if (button == 1) {
			listening = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!listening) {
			return false;
		}
		if (event.isEscape() || event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
			bind.set(InputConstants.UNKNOWN);
		} else {
			bind.set(InputConstants.getKey(event));
		}
		listening = false;
		return true;
	}

	@Override
	public boolean capturesKeys() {
		return listening;
	}
}
