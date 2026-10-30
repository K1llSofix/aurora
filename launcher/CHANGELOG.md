# Aurora Launcher changelog

What changed in each version of Aurora Launcher. The Aurora mod has a changelog of its own (`../CHANGELOG.md`); the launcher shows both in "What's new" after an update.

## 1.1.2 — 2026-10-31

- A new offline account needs a Microsoft sign-in on this computer first, once. Accounts you already have keep working.
- The launcher has its own Client ID, so the Azure Client ID field is gone.
- Until Mojang approves the launcher, the sign-in only unlocks offline accounts: playing on a licensed account is not available yet.

## 1.1.1 — 2026-10-30

- Multiplayer and chat work on offline accounts in Minecraft 1.16.4, 1.16.5 and the snapshots around them. The game used to grey out the button and ask you to check your Microsoft account settings.

## 1.1.0 — 2026-10-28

### New pages

- **Worlds.** The singleplayer worlds of an installation with their icon, game mode, version, size and when they were last played. "Play" starts the game straight into a world. One click makes a backup (a zip in `backups/`, named like the game's own "Make Backup"), and a backup comes back as a new world next to the others, so nothing is overwritten. Deleting goes to the Recycle Bin.
- **Screenshots.** The screenshots of every installation in one grid, newest first, with a filter by installation. A click opens one large: the arrow keys flip, and it can be copied to the clipboard, shown in its folder or deleted.

### Mods and installations

- **Updates in one click.** "Check for updates" on the Mods page finds newer versions of your mods, resource packs and shader packs on Modrinth for the installation's Minecraft version. "Update all" or one at a time; the old file goes to the Recycle Bin, and what was switched on (or the active shader) stays so.
- **Make a copy** of an installation with its own folder: mods, packs and settings, worlds and screenshots if you want them.
- **Export as .mrpack.** Mods, resource packs and shader packs that Modrinth knows are linked, everything else and the settings go inside the file. Accounts and waypoints never do.
- **Carry over settings** from another installation: game settings with key binds, Aurora's settings and profiles, the server list. Replaced files are kept as `.bak`.

### When the game crashes

- The crash window names the likely cause in plain words: not enough memory, a missing or incompatible mod, a mod for another version, a Java that is too old, the graphics driver, a damaged world. Most come with a fix in one click: more memory, switch the mod off, install the missing one, use the launcher's Java.
- **Share the log** uploads the game log to mclo.gs (after asking) and copies the link.

### Settings and more

- **Graphics card:** laptops can ask Windows for the discrete GPU for the game ("Powerful").
- **Colours of the mod's theme:** the launcher can take the colours of the theme picked in Aurora.
- **What's new** after an update, and the whole history on the About page.
- **Favourite servers** stay at the top of the list.
- Bundles Aurora 1.1.0.

## 1.0.2 — 2026-10-27

- An Aurora build you put into an installation's `mods` folder yourself stays there, even under the same version number, until the launcher has a newer version. The launcher remembers which build it installed and replaces only its own.
- Bundles Aurora 1.0.1.

## 1.0.1 — 2026-10-22

- The launcher updates itself from GitHub releases: it downloads a new version in the background, checks it, and installs it when you close it or press "Restart now".

## 1.0.0 — 2026-10-22

First release: one-click install of Minecraft, Fabric and Aurora, Microsoft and offline accounts, a live server list, the Modrinth catalog of mods, resource packs, shaders and modpacks, shortcuts and an offline skin editor.
