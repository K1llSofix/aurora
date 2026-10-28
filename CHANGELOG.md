# Changelog

What changed in each version of the Aurora mod. [Aurora Launcher](launcher/README.md) has version numbers of its own and installs the newest Aurora by itself.

## 1.1.0 — 2026-10-28

For Minecraft 26.3, Fabric Loader 0.19.5+ and Fabric API 0.164.0+26.3 or newer.

### HUD

- **Item Cooldowns.** Seconds left on your ender pearls, wind charges, chorus fruit and a shield an axe knocked out of use, with a bar; vanilla only shades the hotbar slot. Horizontal or vertical, tenths of a second on or off.
- **Pickups.** "+16 Cobblestone" slides in at the side when you pick something up; more of the same adds up in one row, and the row fades after a few seconds (2–10).
- **Saturation** (on by default). Your hidden saturation as a golden line under the hunger bar, and while you hold food, the shanks it would fill blink softly.
- **Server TPS.** The server's tick rate, worked out from its time updates (asked directly in singleplayer). Next to the ping it tells server lag from your own.

### Visual

- **Northern Lights** (on by default). Slow curtains of green light fading to violet over the northern horizon at night, drawn with the sky itself, so every block stands in front of them and the stars shine through. Not by day, not in the rain. Natural or theme colours, brightness and speed.
- **Item Physics.** Dropped items lie on the ground instead of hovering and spinning: ingots and tools on their side, blocks standing, each turned its own way. They tumble while they fly and drift in water.
- **Motion Blur.** Fast turns leave a soft trail, 10 to 90 %. It is a post effect of the game applied to the world only, so the HUD and menus stay sharp.
- **Name Tags.** A dark glass background tinted with the theme (or vanilla, or none), its opacity and size, and your own name over your head in third person. A team that hides names still wins.
- **Camera & Overlays:** a lower shield in first person, and a smaller or hidden totem animation.

### Utility

- **Smart Tooltips** (on by default). A shulker box shows what is inside as a grid of slots in its colour, a filled map shows its picture, food shows how much hunger and saturation it restores, and a worn tool shows its durability.
- **Inventory Search** (on by default). A search box above every chest and inventory; Ctrl+F jumps into it. Matching items glow and the rest dim, also by what is inside shulker boxes and bundles. It only highlights, nothing is moved for you.
- **Waypoints** (on by default). **B** puts a point where you stand: a soft beam and its name with the distance, seen from afar. The place you died is marked by itself. Rename, recolour, hide or delete them in "Waypoints of this world". They are your own coordinates and show nothing about other players.
- **Chat:** Ctrl+F in the open chat searches it; the chat shows only matching messages and counts them.
- **Screenshots:** a gallery of all your screenshots with copy to clipboard, the folder and delete.

## 1.0.1 — 2026-10-27

For Minecraft 26.3, Fabric Loader 0.19.5+ and Fabric API 0.164.0+26.3 or newer.

### New modules

- **TNT Timer** (Visual, on by default). The seconds left until a lit TNT explodes, to a tenth of a second, right above the block. The number is green while there is time, yellow at two seconds and red in the last one. The colors and the background can be turned off, and the size goes from 50 to 200 %. Like the TNT itself, the timer is not visible through walls.
- **Reconnect** (Utility, on by default). After a kick or a lost connection, the disconnect screen gets a "Reconnect" button that takes you back to the same server. With "Reconnect automatically" (off by default) it rejoins by itself after 3 to 60 seconds (5 by default), counting down on the button. After 5 attempts in a row it waits for a click, so a server that keeps refusing is not flooded. Realms are not supported.

### Render optimizer

Its page gets two new sections, Frames and Memory, with status lines that show what is going on right now.

- **Latest frames only** (off by default). The screen always gets the newest finished frame: no tearing and no frames waiting in a queue behind VSync, so there is less input lag, and the frame rate is no longer tied to the monitor's refresh rate. It works with the Vulkan renderer (Video Settings) when the driver supports it; otherwise the "Frame output" line says so.
- **Stable frame rate** (off by default). Measures how long frames take and holds the frame rate just under what your PC keeps up even on its slower frames, so it stays even instead of jumping up and down. The limit drops at once when the game gets heavier and climbs back slowly; the "Held at" line shows it. If your own frame limit is already lower, that one stays.
- **Clean memory behind loading screens** (on by default). Collects garbage right after a world has loaded and after leaving one, on a thread of its own, so what loading left behind is not collected in the middle of a fight.
- The "Garbage collector" line shows which collector the game runs with. With ZGC, which Aurora Launcher starts the game with, pauses stay under 1 ms.

### Hand View

- The item in your hand can now be turned as well as moved and resized: Tilt, Turn and Roll, from −180° to 180°. The left hand mirrors the right one.

## 1.0.0 — 2026-10-22

First public release for Minecraft 26.3.

- Mod menu with 8 themes, instant search and a HUD editor with snapping
- 20 HUD elements, including an item counter and FPS with 1% low and a frame time graph
- Crosshair editor with a live first-person preview
- Profiles: PvP, Survival, Builder, Minimal and Streaming, share codes and a profile per server
- Render optimizer with one-click presets for weak PCs
- Mouse Tweaks, screenshots to the clipboard, Discord status, command binds, toggle sprint
- Animated main menu, new pause menu and loading screens, in-game account switcher
- English and Russian
