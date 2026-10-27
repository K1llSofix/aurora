# Aurora Client — visuals and HUD for Minecraft 26.3

**[Download Aurora Launcher](https://github.com/K1llSofix/aurora/releases/latest)** · [Aurora Client on Modrinth](https://modrinth.com/mod/L7X9iCgB) · [Changelog](CHANGELOG.md)

A client mod for **Fabric** in the spirit of Lunar Client and LabyMod: a searchable, themed mod menu, a draggable HUD, a crosshair editor with a live preview, zoom, freelook, cosmetics like jump circles and trails, its own title screen, pause menu, chat, player list and loading screens, **profiles** (PvP, survival, your own) that switch automatically per server, an **optimizer** for weak PCs, Mouse Tweaks, screenshots straight to the clipboard, a Discord status, command binds and account switching right in the game. The whole interface is drawn as vector shapes (anti-aliased rounded corners, soft shadows) and text uses the Inter font with an atlas for the current GUI scale, so it stays sharp at any window size.

The mod comes with its own **[Aurora Launcher](launcher/README.md)**: it installs Minecraft, Fabric, Fabric API and Aurora with one button, keeps your accounts, shows a live server list, the Modrinth catalog of mods, resource packs, shaders and modpacks, makes shortcuts that start the game straight away, and lets you paint the skin of an offline account.

Every feature is purely visual and gives no PvP advantage: no ESP, no tracers, no autoclickers. Hitboxes, like vanilla F3+B, are not visible through walls and are not shown for invisible entities.

| Title screen | Mod menu |
|---|---|
| ![](screenshots/01-main-menu.jpg) | ![](screenshots/02-mod-menu.jpg) |
| **Settings and the color picker** | **HUD editor** |
| ![](screenshots/03-settings-color-picker.jpg) | ![](screenshots/04-hud-editor.jpg) |
| **China hat and jump circle** | **HUD and the glass hotbar** |
| ![](screenshots/05-china-hat-jump-circle.jpg) | ![](screenshots/06-hud-glass-hotbar.jpg) |
| **The Binds tab** | **Target HUD with a mob head** |
| ![](screenshots/07-binds.jpg) | ![](screenshots/08-target-hud-mob-head.jpg) |
| **"Sunset" menu background** | **"Synthwave" menu background** |
| ![](screenshots/09-background-sunset.jpg) | ![](screenshots/10-background-synthwave.jpg) |
| **Chat, scoreboard, boss bar and hitboxes** | **Player list with ping in ms** |
| ![](screenshots/11-chat-scoreboard-hitboxes.jpg) | ![](screenshots/12-tab-list.jpg) |
| **Pause menu** | **Accounts** |
| ![](screenshots/13-pause-menu.jpg) | ![](screenshots/14-accounts.jpg) |

## The launcher

| Aurora Launcher | Servers with players and ping |
|---|---|
| ![](screenshots/15-launcher-home.jpg) | ![](screenshots/16-launcher-servers.jpg) |
| **Mods and the Modrinth catalog** | **Settings** |
| ![](screenshots/17-launcher-mods.jpg) | ![](screenshots/19-launcher-settings.jpg) |

The easiest way to play with Aurora is the launcher installer from the [releases page](https://github.com/K1llSofix/aurora/releases/latest) (built from the [`launcher`](launcher/README.md) folder with `npm run dist`). The launcher downloads the game, Java 25 from Mojang, Fabric, Fabric API and the mod, updates itself from GitHub releases and takes new builds of the mod from Modrinth. See the [launcher README](launcher/README.md) for details.

## Manual installation

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.19.5+** for Minecraft **26.3**.
2. Put into the `mods` folder:
   - `aurora-<version>+26.3.jar` (from `build/libs/` after a build, or from Modrinth);
   - [Fabric API](https://modrinth.com/mod/fabric-api) **0.164.0+26.3** or newer.
3. Java 25 is required (the official launcher installs it for 26.x).

## Controls

| Key | Action |
|---|---|
| **Right Shift** | Aurora mod menu |
| **C** (hold) | Zoom; the mouse wheel changes the magnification |
| **Left Alt** (hold) | Freelook (look around without turning your character) |

All binds live on the **Binds** tab of the mod menu: Aurora's own keys (menu, zoom, freelook), **command binds** and an on/off key for every module. Click a key, then press a new one; Esc or Backspace clears the bind, a right click unbinds it. Conflicts with other binds and vanilla controls are highlighted in yellow, and hovering shows what they clash with. There is a search field and "Reset all". Letter keys are labelled in Latin whatever your keyboard layout. Toggling a module with its key shows a notification in the bottom right corner.

### Command binds

In the **Commands** section of the Binds tab, click **"+ Add"**, type a command (for example `/spawn`, `/home`, `/warp pvp`) and assign a key — side mouse buttons work too. Pressing the key in game sends the command (text without `/` goes to the chat as a message) and shows a notification. Repeats are rate-limited so you cannot spam by accident. The cross on the right deletes a bind. The "Command Binds" module in the Utility category turns them all on or off at once.

## Mod menu

- Categories on the left (All mods / HUD / Visual / Utility / Performance) with module counts and an indicator that slides between them. At the bottom are **Profiles**, **Binds** and **Settings**, with the HUD Editor button below.
- **Search**: just start typing, the search field takes focus by itself.
- A module card: the switch turns it on or off, a click on the card opens its settings, a **right click** on the card toggles it quickly.
- Settings: switches, sliders, segmented buttons and dropdowns, a color picker (saturation/brightness, hue, opacity, a "Chroma" mode, preset colors), key binding and reset to defaults.
- **Client settings** (the gear at the bottom left): 8 themes (Aurora, Ocean, Sunset, Rose, Emerald, Crimson, Mono and a custom color), animation speed, background blur, HUD style (Modern / Classic / Minimal), HUD font (Inter / Minecraft), background opacity, the accent bar, text color and shadow, and the Aurora main menu.

## Profiles

The **Profiles** page keeps whole setups: which modules are on, their settings and the HUD layout. Your theme, keys and command binds stay yours when a profile loads.

- **Ready-made:** PvP (keystrokes, CPS, reach, combo, Target HUD, a crosshair with movement spread and cooldown, low fire), Survival (coordinates with biome and Nether coordinates, compass, clock, item counter), Builder (a filled outline for the targeted block, always day and clear skies), Minimal and Streaming.
- **Your own:** "Save current" makes a profile out of what is on right now. Rename it right in its row; next to it are "Save the current setup into this profile" and delete.
- **Sharing:** the copy button puts a code like `AURORA1:...` on the clipboard, and "Paste code" adds a profile a friend sent you.
- **Undo:** if you do not like a profile you loaded, the arrow button undoes the last load and brings the previous setup back.
- **Per server:** type a server address into "Auto on server" (`hypixel.net` also covers `mc.hypixel.net`, several can be separated by commas), and the profile turns on when you join that server and everything switches back when you leave.

Profiles are stored in `config/aurora/profiles`.

## Performance

The **Performance** category holds the "Render optimizer", a module that gives more FPS without changing the game: distant mobs, item frames, armor stands, holograms, dropped items, chests, signs and banners are not drawn beyond a set distance (each kind has its own), the number of particles is capped, and far-away particles are not created at all. When the game window is in the background, FPS drops to the value you choose (15 by default) so the PC is not loaded. Players are not hidden by default, and dragons, withers and glowing entities are always visible.

**Frames:**

- **Latest frames only** switches the window to mailbox presentation: a finished frame replaces one still waiting for the screen, so the monitor always shows the newest picture. With VSync on, the game is no longer capped at the refresh rate and frames do not queue up behind it (less input lag); with VSync off, the picture no longer tears. It needs the Vulkan graphics API (Video Settings); on OpenGL the line under the switch says so.
- **Stable frame rate** measures how long frames really take (without the limiter's waiting) and holds the game at the rate even the slowest tenth of frames reaches. The cap drops at once when frames get heavier and rises slowly, so the frame rate stays even instead of jumping up and down.

**Memory:** garbage is collected right after a world has loaded and after leaving one, behind the loading screen, so leftovers of loading are not collected in the middle of a fight. The page also shows which garbage collector the game runs with: Aurora Launcher starts 26.x with ZGC (Mojang's own recommended arguments), whose pauses stay under a millisecond.

**Quick setup** also sets the vanilla graphics in one click: "For a weak PC" (render distance 6, no clouds, minimal particles, fast graphics), "Balanced" and "Quality".

## HUD editor

The **HUD Editor** button in the menu.

- Drag elements with the mouse: they snap to the screen edges, the center and each other (the guides light up). **Alt** moves them freely.
- **Mouse wheel** over an element: scale 50–300 %.
- **Right click**: the element's settings; **arrow keys**: move by 1 px (by 10 with Shift); **R**: reset the position; **Delete**: hide the element.
- On the left is a scrollable list of all elements with switches.
- Positions are kept relative to the nearest edge, so the layout survives a change of resolution and GUI scale.

## Modules

**HUD (20):** FPS (updated 4 times a second, optionally with 1% low, a frame time graph and color by FPS) · CPS · Keystrokes (WASD/mouse/space, CPS on the buttons) · Armor Status (durability in % or as a number, colored by wear) · **Item counter** (arrows, pearls, golden apples, totems, potions, fireworks, blocks, food and how many of the held item you have; turns red when low and flashes when it changes) · Potion Effects (icons, time, progress bars, blinking before they end, hiding the vanilla icons) · Coordinates (direction, biome, Nether coordinates) · Target HUD (head, health with a damage "trail", absorption, armor, a W/L indicator) · Compass strip · Ping · Clock / session timer · Speed · Reach Display · Combo Counter · Memory · Server Address · Toggle Sprint status · **Scoreboard** · **Boss Bar** · **Player List** · **Chat**.

**Visual (17):** **Hitboxes** · Crosshair (6 shapes, color, outline, movement spread, target highlight, a cooldown ring, a **live preview** in its settings) · Block Outline (color, thickness, fill) · Zoom (smooth, mouse wheel, cinematic camera, lower sensitivity) · Freelook · Fullbright · Hit Color · Particles (a multiplier, crits and sharpness sparks on every hit) · Jump Circles · China Hat · Trails · Time Changer (a fixed time or an animated cycle) · Clear Weather · Hand View (offset, tilt, turn, roll and size of the held item; mirrored for the left hand) · Hotbar (smooth selection, glass style) · Camera & Overlays (no hurt shake, lower fire, no pumpkin overlay) · **TNT Timer** (seconds until lit TNT explodes, above it, from green to red; walls hide it like the TNT itself).

**Utility:** Toggle Sprint (through the vanilla toggle mode, so every sprinting rule still applies) · Command Binds · **Mouse Tweaks** · **Screenshots** · **Discord status** · **Reconnect** (a "Reconnect" button on the disconnect screen after a kick or a lost connection; optionally reconnects by itself after a delay, at most five times in a row).

**Performance:** the "Render optimizer" module (see above).

**Crosshair preview:** the crosshair settings show a small first-person scene on the right, and the crosshair in it changes as you move the sliders. The backdrop can be a meadow, snow, desert, cave, the Nether or night (real block textures), so you can check the crosshair is visible everywhere. The "Still / Walk / Sprint / Jump" buttons show the movement spread. A zombie walks past: when the crosshair is on it you see the target highlight, and a click on the scene is a hit with the cooldown ring. 1x/2x/4x zoom helps to set the thickness to the pixel.

**Mouse Tweaks** is a port of the original Mouse Tweaks mod and works in every inventory, including creative:

- **Shift + left click and drag over slots** (empty cursor): every stack the mouse passes over moves to the other part of the screen, as with a Shift-click.
- **Left click on a stack and drag** over slots with the same item: they gather on the cursor; with Shift held those items move to the other part of the screen.
- **Right click with an item on the cursor and drag**: one item goes into every slot on the way, and one more on another pass.
- **Mouse wheel over a stack**: moves items one by one to the other part of the screen (chest and inventory, hotbar and inventory), and back the other way. Over a crafting result, the wheel crafts and puts the results away.

Every feature can be switched off in the module's settings, and the wheel lets you pick the direction (including "to the other inventory"), where items are taken from and how many move per notch. Everything is done with ordinary clicks, so it works on any server.

**Discord status** (Rich Presence): your Discord profile shows "Playing Aurora Client" and where you are: in the title screen, choosing a server, in singleplayer, on LAN, on Realms or on a server with its address, plus the dimension and the time in game. A licensed account also shows its skin's head next to the logo. Under the status there is a "Get Aurora Client" button leading to the mod's page from `homepage` in `fabric.mod.json`. The server address, world name, dimension, head, time and the button can each be hidden in the module's settings. The Discord app has to be running on the same computer; if you start it later, the status appears by itself.

**Screenshots:** F2 copies the shot straight to the clipboard (paste it into Discord or Telegram with Ctrl+V), a notification with a preview appears in the corner, and the chat message gets a link to the folder.

**Hitboxes** are F3+B done nicely: a frame or "corners" with adjustable thickness, a soft fill that fades out towards the top, the look direction and eye level, a highlight for the entity under the crosshair, colors by type (players in the theme color, hostile mobs red, passive mobs — animals, villagers and golems — green, items yellow, projectiles light blue) or your own color. You choose whose hitboxes to show and how far. Walls hide hitboxes just as they hide the entity itself.

**Chat:** lines sit on one rounded panel, new messages slide in from below, and the input field is glass. Messages show the time and the sender's head (for player messages and for system messages that start with a name), and identical messages in a row collapse into "×3". Messages where someone wrote your name are highlighted with an accent bar and play a short sound. A **right click** on a message copies its text. Links, hover text and clicks on messages work as in vanilla.

The **Scoreboard** became a HUD element: you can move and scale it in the HUD editor, it is drawn in the chosen HUD style, and the red score numbers can be hidden. The **Boss Bar** moves too: smooth bars in the boss color, with notches for segmented bars. The **Player List** is a glass panel with player heads, ping in milliseconds colored by quality, scores, the server header and footer, and a player count.

**Toasts** (advancements, recipes, system messages, music, friends) got glass cards with an accent bar.

**Pause menu:** the vanilla menu's buttons (with all their features: friends, server links and dialogs, reports) are gathered in a column on the left under the Aurora header, with an added "Aurora Mods" button. On the right are the "Singleplayer" or "Server" card (name, game mode, difficulty, LAN or address, ping, players) and the "Session" card (time in game, coordinates, dimension, FPS), and between them a player model that follows the mouse. Can be switched off in the client settings.

**Loading screens:** at startup, instead of the Mojang logo, an Aurora splash plays over the animated menu scene with a smooth loading bar that flows right into the title screen. Loading a world, joining a server, creating a world and short waits like "Saving world" show the same scene, a glass card with a ring spinning around the logo, the progress (when it is known) and an Aurora tip at the bottom. Can be switched off in the client settings.

## Accounts

Click the profile card in the top right corner of the title screen to open the account switcher. You can switch accounts without restarting the game (outside a world only); the choice is remembered and restored at the next start.

- **From the launcher**: the account the game was started with; you can always go back to it. When the game is started from Aurora Launcher, the account picked in the launcher wins over the one you picked in game last time.
- **Offline**: any name (3–16 Latin letters, digits or `_`) for singleplayer, LAN and servers that do not check licenses.
- **Microsoft**: sign-in through the browser. Aurora shows a short code, you open microsoft.com/link and enter it, so Aurora never sees your password. This needs the **Client ID** of an Azure application: register one on portal.azure.com (personal Microsoft accounts, *public client flows* enabled) and apply to Mojang for access to Minecraft sign-in: [aka.ms/mce-reviewappid](https://aka.ms/mce-reviewappid). The Client ID is entered once, the first time you click "+ Microsoft". Mojang does not let unapproved applications in, and Aurora says so.

Switching changes the whole session: chat message signing, friends, reports, telemetry and Realms. An offline account's skin painted or uploaded in the launcher is shown on your character in game. Only you see it: servers do not pass offline skins to other players. Accounts are stored in `config/aurora/accounts.json`; on Windows, Microsoft tokens are encrypted with DPAPI (only your Windows user on this PC can read them).

**Aurora main menu:** a live animated background, the profile card and quick access to the mods. Can be switched off in the client settings.

**Menu background** (client settings → "Menu background") replaces the rotating panorama in every menu; on screens like "Singleplayer" it shows blurred under a dark overlay:

- **Northern lights** (the default): a mountain lake at night with shimmering aurora curtains, snowy peaks, a spruce shore, reflections in the water, ripples, mist over the water, twinkling stars, the Milky Way and shooting stars. The aurora's color follows the theme a little.
- **Sunset**: a warm evening in the hills in the style of Firewatch, with hazy mountain layers, a low sun with rays, drifting clouds, a flock of birds, fireflies and a fire lookout tower with a lit window.
- **Synthwave**: a retro sun in stripes, a neon grid running towards you, wireframe mountains, swaying palm trees and faint VHS lines.
- **Your picture**: PNG, JPG, BMP or an **animated GIF** from the `config/aurora/backgrounds` folder (the "Open folder" button in the settings). The picture is fitted to the screen and slowly drifts and zooms (the Ken Burns effect), and pixel art stays crisp. Several files make a slideshow with a smooth change about every 40 seconds. New files are picked up without a restart.
- **Minecraft panorama**: the vanilla panorama.

The scenes are drawn in real time and do not depend on the resolution. Their layers follow the mouse (parallax, can be turned off). The animation speed comes from the vanilla "Panorama Scroll Speed" setting in Accessibility: at zero the background stands still.

**Aurora style for game menus:** "Singleplayer", "Multiplayer", the options, the pause menu and the other vanilla screens get the same look: glass buttons with an accent glow, rounded input fields, sliders and checkboxes, world and server cards, accent selection in lists, thin scrollbars, new tooltips and a dark background with a soft glow. Every feature of the vanilla screens works as before; only the drawing changes. Can be switched off in the client settings.

**Target HUD** shows a live 3D portrait of the mob's head (any mob, including mobs from other mods), and a player's face from their skin.

The interface is fully translated into English and Russian; the language follows the game's setting.

## Building from source

JDK 25 is required.

```bash
./gradlew build
```

The mod jar: `build/libs/aurora-<version>+26.3.jar`. Start a test client with `./gradlew runClient`.

The Discord status is shown on behalf of the Discord application whose ID is in `DiscordModule.APP_ID` (and in `launcher/src/main/defaults.js`, `DISCORD_APP_ID`). The application's name is what friends see after "Playing", and the logo lives in its Rich Presence → Art Assets under the key `aurora` (the picture is `launcher/build/discord-logo.png`).

## Releasing a new version

1. Raise `mod_version` in `gradle.properties` (for example `1.0.1`), write down what changed in [`CHANGELOG.md`](CHANGELOG.md) and build the mod: `./gradlew build`.
2. Upload `build/libs/aurora-<version>+26.3.jar` to [Modrinth](https://modrinth.com/mod/L7X9iCgB) (Fabric loader, Minecraft 26.3, client only, Fabric API as a required dependency) and to CurseForge. What goes into each field is in [`publish/MODRINTH.md`](publish/MODRINTH.md).
3. That's it: once an hour and before every launch, Aurora Launcher looks up the versions of the Aurora project on Modrinth (by its ID in `launcher/src/main/defaults.js`, `AURORA_MODRINTH`) and installs a newer one by itself if it really is Aurora inside, so a mod update needs no new launcher. A build for a new Minecraft version published on Modrinth becomes available in the launcher right away too.

The launcher skips versions of the "alpha" type and installs "release" and "beta" ones.

## Configuration

`config/aurora.json` holds the module states, settings, HUD positions and command binds. `config/aurora/accounts.json` holds the saved accounts. `config/aurora/profiles/` holds the profiles, their server bindings and the backup for undoing a load. Everything is saved when the menu closes and when the game exits. If the file is damaged, Aurora keeps a copy as `aurora.json.broken` and starts with the default settings.

## Code layout

The launcher (Electron) lives in the [`launcher`](launcher/README.md) folder and is described separately.

```
dev.aurora
├── Aurora.java            entry point: modules, binds, the HUD layer, world events
├── account/               accounts: Microsoft sign-in (device code), storage, session switching
├── config/                JSON storage (atomic writes), profiles and their share codes
├── setting/               setting types: boolean, number, mode, color (with chroma), key
├── module/                Module, HudModule (positioning), ClientSettings
│   ├── hud/               HUD elements
│   ├── visual/            visual modules
│   ├── utility/           utilities: toggle sprint, command binds, Mouse Tweaks, screenshots
│   └── performance/       the render optimizer
├── gui/
│   ├── background/        menu backgrounds: scenes, mountain and forest silhouettes, stars, your pictures and GIFs
│   ├── render/            Render2D (anti-aliased vector shapes), FontRenderer + GlyphAtlas (the Inter font), World3D
│   ├── widget/            switch, text field, setting rows, icons, the crosshair preview
│   └── screen/            mod menu, HUD editor, title screen
├── skin/                  offline account skins from the launcher
├── mixin/                 targeted changes to vanilla code
└── util/                  animations, colors, CPS and combat trackers, pictures to the Windows clipboard
```

Aurora is distributed under the MIT License (`LICENSE`). The Inter font is distributed under the SIL Open Font License 1.1 (`assets/aurora/fonts/Inter-OFL.txt`).

The Mouse Tweaks module is ported from [Mouse Tweaks](https://github.com/YaLTeR/MouseTweaks) by Ivan Molodetskikh (YaLTeR), BSD 3-Clause License, Copyright (c) 2023, Ivan Molodetskikh. The license text is in `assets/aurora/licenses/MouseTweaks-LICENSE.txt`.
