package dev.aurora.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.utility.CommandBindsModule;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** A command bind on the Binds page: an editable command, its key and a delete button. */
public final class CommandBindRow extends BindRow {
	private static final float DELETE = 16;

	private final CommandBindsModule.CommandBind command;
	private final TextField field;
	private final Runnable onDelete;
	private final Animation deleteHover = new Animation(0, 16);
	private float dx, dy;

	/** {@code bind} must be the same instance that is in {@code all}, so the row does not conflict with itself. */
	public CommandBindRow(CommandBindsModule.CommandBind command, Bind bind, List<Bind> all, Runnable onDelete) {
		super(bind, all);
		this.command = command;
		this.onDelete = onDelete;
		this.field = new TextField(I18n.get("aurora.ui.binds.command_placeholder"), 256).plain();
		field.setValue(command.command());
		field.onChange(() -> {
			command.setCommand(field.value());
			Aurora.config().markDirty();
		});
	}

	/** The command bind as a generic bind, so conflicts and "reset all" treat it like the rest. */
	public static Bind bindOf(CommandBindsModule.CommandBind command) {
		return new Bind() {
			@Override
			public String name() {
				return command.command().isBlank() ? I18n.get("aurora.ui.binds.command_empty") : command.command();
			}

			@Override
			public @Nullable ItemStack icon() {
				return null;
			}

			@Override
			public InputConstants.Key key() {
				return command.key();
			}

			@Override
			public void set(InputConstants.Key key) {
				command.setKey(key);
				Aurora.config().markDirty();
			}

			@Override
			public void reset() {
				set(InputConstants.UNKNOWN);
			}

			@Override
			public boolean allowsMouse() {
				return true;
			}
		};
	}

	public CommandBindsModule.CommandBind command() {
		return command;
	}

	public void focusField() {
		field.setFocused(true);
	}

	@Override
	protected float rightInset() {
		return DELETE + 6;
	}

	@Override
	protected void renderIcon(GuiGraphicsExtractor g, float iconX, float cy) {
		Render2D.roundRect(g, iconX, cy - 6, 12, 12, 3.5f,
			Render2D.Paint.diagonal(iconX, cy - 6, 12, 12, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
		FontRenderer.BOLD.drawCentered(g, "/", iconX + 6.2f, cy + 0.2f, 8, Ui.c(Theme.onAccent()));
	}

	@Override
	protected void renderLabel(GuiGraphicsExtractor g, int mx, int my, float left, float right) {
		field.x = left - 4;
		field.y = y + (HEIGHT - 18) / 2f;
		field.w = Math.max(40, right - field.x);
		field.h = 18;
		field.render(g, mx, my);
	}

	@Override
	protected void renderExtra(GuiGraphicsExtractor g, int mx, int my) {
		dx = x + w - 12 - DELETE;
		dy = y + (HEIGHT - DELETE) / 2f;
		boolean hov = Ui.inside(mx, my, dx, dy, DELETE, DELETE);
		float h = deleteHover.target(hov).get();
		if (h > 0.01f) {
			Render2D.circle(g, dx + DELETE / 2f, dy + DELETE / 2f, DELETE / 2f, Ui.c(ColorUtil.fade(Theme.DANGER, 0.18f * h)));
		}
		Render2D.cross(g, dx + DELETE / 2f, dy + DELETE / 2f, 7, 1.3f,
			Ui.c(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.DANGER, h)));
		if (hov) {
			g.setTooltipForNextFrame(Component.translatable("aurora.ui.binds.command_delete"), mx, my);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (listening) {
			return super.mouseClicked(mx, my, button);
		}
		boolean inside = Ui.inside(mx, my, x, y, w, HEIGHT);
		if (button == 1 && Ui.inside(mx, my, dx, dy, DELETE, DELETE)) {
			field.setFocused(false);
			onDelete.run();
			return true;
		}
		if (button == 1 && Ui.inside(mx, my, field.x, field.y, field.w, field.h)) {
			field.setFocused(true);
			return true;
		}
		if (field.focused()) {
			field.setFocused(false);
			if (!inside) {
				return false;
			}
		}
		if (!inside) {
			return false;
		}
		if (button == 3) {
			bind().set(InputConstants.UNKNOWN);
			return true;
		}
		if (button == 1 && Ui.inside(mx, my, bx, by, bw, bh)) {
			listening = true;
			return true;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (field.focused()) {
			if (event.isEscape() || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				field.setFocused(false);
				return true;
			}
			return field.keyPressed(event);
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return field.focused() && field.charTyped(event);
	}

	@Override
	public boolean capturesKeys() {
		return listening || field.focused();
	}

	@Override
	public boolean typing() {
		return field.focused();
	}
}
