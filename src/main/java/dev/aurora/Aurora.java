package dev.aurora;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.aurora.account.AccountManager;
import dev.aurora.config.ConfigManager;
import dev.aurora.config.Profiles;
import dev.aurora.gui.HudLayer;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.screen.ModMenuScreen;
import dev.aurora.module.ClientSettings;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import dev.aurora.module.hud.ArmorHud;
import dev.aurora.module.hud.BossBarHud;
import dev.aurora.module.hud.ChatModule;
import dev.aurora.module.hud.ClockHud;
import dev.aurora.module.hud.ComboHud;
import dev.aurora.module.hud.CompassHud;
import dev.aurora.module.hud.CoordinatesHud;
import dev.aurora.module.hud.CpsHud;
import dev.aurora.module.hud.FpsHud;
import dev.aurora.module.hud.ItemCounterHud;
import dev.aurora.module.hud.KeystrokesHud;
import dev.aurora.module.hud.MemoryHud;
import dev.aurora.module.hud.PingHud;
import dev.aurora.module.hud.PotionHud;
import dev.aurora.module.hud.ReachHud;
import dev.aurora.module.hud.ScoreboardHud;
import dev.aurora.module.hud.ServerHud;
import dev.aurora.module.hud.SpeedHud;
import dev.aurora.module.hud.TabListModule;
import dev.aurora.module.hud.TargetHud;
import dev.aurora.module.performance.PerformanceModule;
import dev.aurora.module.utility.CommandBindsModule;
import dev.aurora.module.utility.DiscordModule;
import dev.aurora.module.utility.ReconnectModule;
import dev.aurora.module.utility.MouseTweaksModule;
import dev.aurora.module.utility.ScreenshotsModule;
import dev.aurora.module.utility.ToggleSprintModule;
import dev.aurora.module.visual.BlockOutlineModule;
import dev.aurora.module.visual.ChinaHatModule;
import dev.aurora.module.visual.CrosshairModule;
import dev.aurora.module.visual.FreelookModule;
import dev.aurora.module.visual.FullbrightModule;
import dev.aurora.module.visual.HandViewModule;
import dev.aurora.module.visual.HitColorModule;
import dev.aurora.module.visual.HitboxesModule;
import dev.aurora.module.visual.TntTimerModule;
import dev.aurora.module.visual.HotbarModule;
import dev.aurora.module.visual.JumpCirclesModule;
import dev.aurora.module.visual.OverlaysModule;
import dev.aurora.module.visual.ParticlesModule;
import dev.aurora.module.visual.TimeChangerModule;
import dev.aurora.module.visual.TrailsModule;
import dev.aurora.module.visual.WeatherModule;
import dev.aurora.module.visual.ZoomModule;
import dev.aurora.util.CombatTracker;
import dev.aurora.util.Session;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class Aurora implements ClientModInitializer {
	public static final String MOD_ID = "aurora";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static ModuleManager modules;
	private static ClientSettings client;
	private static ConfigManager config;

	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(id("main"));
	public static KeyMapping menuKey;
	public static KeyMapping zoomKey;
	public static KeyMapping freelookKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ModuleManager modules() {
		return modules;
	}

	public static ClientSettings client() {
		return client;
	}

	public static ConfigManager config() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		client = modules.register(new ClientSettings());
		registerModules();

		config = new ConfigManager(modules);
		config.load();
		client.applyAnimationSpeed();

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurora.menu", InputConstants.KEY_RSHIFT, KEY_CATEGORY));
		zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurora.zoom", InputConstants.KEY_C, KEY_CATEGORY));
		freelookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurora.freelook", InputConstants.KEY_LALT, KEY_CATEGORY));

		registerRendering();

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> config.save());
		ClientLifecycleEvents.CLIENT_STARTED.register(AccountManager::init);
		// Profiles bound to a server switch in on joining it and back out on leaving.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> Profiles.onJoin(mc)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(Profiles::onLeave));
		LOGGER.info("Aurora ready with {} modules", modules.all().size());
	}

	private void registerModules() {
		// HUD
		modules.register(new FpsHud());
		modules.register(new CpsHud());
		modules.register(new KeystrokesHud());
		modules.register(new ArmorHud());
		modules.register(new PotionHud());
		modules.register(new ItemCounterHud());
		modules.register(new CoordinatesHud());
		modules.register(new TargetHud());
		modules.register(new CompassHud());
		modules.register(new PingHud());
		modules.register(new ClockHud());
		modules.register(new SpeedHud());
		modules.register(new ReachHud());
		modules.register(new ComboHud());
		modules.register(new MemoryHud());
		modules.register(new ServerHud());
		modules.register(new ScoreboardHud());
		modules.register(new BossBarHud());
		modules.register(new TabListModule());
		modules.register(new ChatModule());
		// Visual
		modules.register(new CrosshairModule());
		modules.register(new BlockOutlineModule());
		modules.register(new ZoomModule());
		modules.register(new FreelookModule());
		modules.register(new FullbrightModule());
		modules.register(new HitColorModule());
		modules.register(new ParticlesModule());
		modules.register(new JumpCirclesModule());
		modules.register(new ChinaHatModule());
		modules.register(new TrailsModule());
		modules.register(new TimeChangerModule());
		modules.register(new WeatherModule());
		modules.register(new HandViewModule());
		modules.register(new HotbarModule());
		modules.register(new OverlaysModule());
		modules.register(new HitboxesModule());
		modules.register(new TntTimerModule());
		// Utility
		modules.register(new ToggleSprintModule());
		modules.register(new CommandBindsModule());
		modules.register(new MouseTweaksModule());
		modules.register(new ScreenshotsModule());
		modules.register(new DiscordModule());
		modules.register(new ReconnectModule());
		// Performance
		modules.register(new PerformanceModule());
	}

	private void registerRendering() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud"), HudLayer::render);
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, delta) -> {
			PotionHud potions = modules.get(PotionHud.class);
			if (potions == null || !potions.hidesVanillaEffects()) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (graphics, delta) -> {
			ScoreboardHud scoreboard = modules.get(ScoreboardHud.class);
			if (scoreboard == null || !scoreboard.isEnabled()) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> (graphics, delta) -> {
			BossBarHud bossBar = modules.get(BossBarHud.class);
			if (bossBar == null || !bossBar.isEnabled()) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			CrosshairModule crosshair = modules.get(CrosshairModule.class);
			if (crosshair != null && crosshair.replaces()) {
				crosshair.render(graphics);
			} else {
				vanilla.extractRenderState(graphics, delta);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Vec3 camera = context.levelState().cameraRenderState.pos;
			float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			JumpCirclesModule circles = modules.get(JumpCirclesModule.class);
			if (circles.isEnabled()) {
				circles.render(context.poseStack(), context.submitNodeCollector(), camera);
			}
			ChinaHatModule hat = modules.get(ChinaHatModule.class);
			if (hat.isEnabled()) {
				hat.render(context.poseStack(), context.submitNodeCollector(), camera, partialTick);
			}
			TrailsModule trails = modules.get(TrailsModule.class);
			if (trails.isEnabled()) {
				trails.render(context.poseStack(), context.submitNodeCollector(), camera, partialTick);
			}
			HitboxesModule hitboxes = modules.get(HitboxesModule.class);
			if (hitboxes.isEnabled()) {
				hitboxes.render(context.poseStack(), context.submitNodeCollector(), camera, partialTick);
			}
		});
	}

	private void tick(Minecraft mc) {
		CombatTracker.tick();
		Session.tick(mc);
		while (menuKey.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.gui.setScreen(new ModMenuScreen(null));
			}
		}
		for (Module module : modules.all()) {
			if (module.isEnabled()) {
				module.tick();
			}
		}
		if (mc.gui.screen() == null) {
			config.saveIfDirty();
		}
	}

	/** Command binds for a key or mouse button pressed while playing. */
	public static void onBindPress(InputConstants.Key key) {
		CommandBindsModule commands = modules.get(CommandBindsModule.class);
		if (commands != null) {
			commands.onKey(key);
		}
	}

	/** Toggle keys configured in the mod menu; called from the keyboard press event while playing. */
	public static void onKeyPress(int key) {
		onBindPress(InputConstants.Type.KEYBOARD.getOrCreate(key));
		for (Module module : modules.all()) {
			if (module.keybind().get() == key && module.canToggle()) {
				module.toggle();
				config.markDirty();
				Notifications.push(module.name(),
					I18n.get(module.isEnabled() ? "aurora.notify.enabled" : "aurora.notify.disabled"),
					module.isEnabled() ? Notifications.Kind.ENABLED : Notifications.Kind.DISABLED);
			}
		}
	}
}
