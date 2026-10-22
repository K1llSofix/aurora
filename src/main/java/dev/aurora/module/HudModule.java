package dev.aurora.module;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;

/**
 * A draggable on-screen element. Its position is stored as a fraction of the free space on each
 * axis (0 = left/top edge, 1 = right/bottom edge), so an element in a corner stays in that corner
 * on every resolution and grows away from the edge it is attached to.
 *
 * <p>Default placement is given as an anchor plus an offset in GUI units and converted the first
 * time the element is drawn, so defaults line up exactly at any window size.
 */
public abstract class HudModule extends Module {
	private final float anchorX;
	private final float anchorY;
	private final float offsetX;
	private final float offsetY;
	private boolean placed;
	private float relX;
	private float relY;
	private float scale = 1f;
	protected float width = 40;
	protected float height = 12;

	protected HudModule(String id, Item icon, boolean enabledByDefault, float anchorX, float anchorY, float offsetX, float offsetY) {
		this(id, Category.HUD, icon, enabledByDefault, anchorX, anchorY, offsetX, offsetY);
	}

	protected HudModule(String id, Category category, Item icon, boolean enabledByDefault,
						float anchorX, float anchorY, float offsetX, float offsetY) {
		super(id, category, icon, enabledByDefault);
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
	}

	/** Updates {@link #width} and {@link #height}; called right before every render. */
	public abstract void layout(boolean editing);

	/** Draws the element with its top-left corner at (0, 0) in unscaled units. */
	public abstract void render(GuiGraphicsExtractor g, float partialTick, boolean editing);

	/** Whether there is anything to show outside the editor (e.g. no potion effects active). */
	public boolean hasContent() {
		return true;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public float scaledWidth() {
		return width * scale;
	}

	public float scaledHeight() {
		return height * scale;
	}

	public float scale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = Math.max(0.5f, Math.min(3f, Math.round(scale * 20f) / 20f));
	}

	/** Converts the default anchor + offset into a stored position once the screen size is known. */
	public void ensurePlaced(int screenWidth, int screenHeight) {
		if (placed) {
			return;
		}
		float x = anchorX * (screenWidth - scaledWidth()) + offsetX;
		float y = anchorY * (screenHeight - scaledHeight()) + offsetY;
		setPosition(x, y, screenWidth, screenHeight);
	}

	public float x(int screenWidth) {
		return relX * Math.max(0, screenWidth - scaledWidth());
	}

	public float y(int screenHeight) {
		return relY * Math.max(0, screenHeight - scaledHeight());
	}

	public void setPosition(float x, float y, int screenWidth, int screenHeight) {
		float freeX = screenWidth - scaledWidth();
		float freeY = screenHeight - scaledHeight();
		relX = freeX <= 0 ? 0 : clamp01(x / freeX);
		relY = freeY <= 0 ? 0 : clamp01(y / freeY);
		placed = true;
	}

	public float relX() {
		return relX;
	}

	public float relY() {
		return relY;
	}

	/** True when the element sits on the right part of the screen, so its contents align right. */
	public boolean anchoredRight() {
		return relX > 0.6f;
	}

	public boolean anchoredBottom() {
		return relY > 0.6f;
	}

	public void resetPosition() {
		placed = false;
		scale = 1f;
	}

	@Override
	public void resetToDefaults() {
		super.resetToDefaults();
		resetPosition();
	}

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		if (placed) {
			JsonObject hud = new JsonObject();
			hud.addProperty("x", relX);
			hud.addProperty("y", relY);
			hud.addProperty("scale", scale);
			o.add("hud", hud);
		}
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		if (o.has("hud") && o.get("hud").isJsonObject()) {
			JsonObject hud = o.getAsJsonObject("hud");
			if (hud.has("x") && hud.has("y")) {
				relX = clamp01(hud.get("x").getAsFloat());
				relY = clamp01(hud.get("y").getAsFloat());
				placed = true;
			}
			if (hud.has("scale")) {
				setScale(hud.get("scale").getAsFloat());
			}
		}
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
