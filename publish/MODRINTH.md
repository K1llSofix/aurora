# Публикация Aurora на Modrinth

Всё, что нужно вписать на [modrinth.com](https://modrinth.com) → **Create a project**. Значения в блоках кода можно копировать как есть.

## 1. Создание проекта

**Project type:** `Mod`

**Name**

```
Aurora Client
```

**URL** — любой свободный адрес (`aurora-client` уже занят). Например:

```
auroraclient
```

Если и он занят, подойдёт `aurora-client-mod` или `aurora-pvp-client`. Aurora Launcher ищет обновления не по адресу, а по **ID проекта** `L7X9iCgB` (вписан в `launcher/src/main/defaults.js`, `AURORA_MODRINTH`), поэтому адрес можно потом менять. Ссылка на страницу в моде тоже идёт по ID: https://modrinth.com/mod/L7X9iCgB.

**Visibility:** `Public`.

**Summary** — до 256 символов, видно в поиске. Лучше английский: так проект находят в поиске и на нём проходит проверка.

```
All-in-one client mod: themed mod menu, draggable HUD, crosshair editor with live preview, per-server profiles, FPS optimizer, Mouse Tweaks and Discord status. Purely visual, no PvP advantage.
```

Русский вариант, если хотите только по-русски:

```
Клиентский мод «всё в одном»: меню модов с темами, перетаскиваемый HUD, редактор прицела с предпросмотром, конфиги под сервер, оптимизация FPS, Mouse Tweaks и статус в Discord. Только визуал.
```

## 2. Настройки проекта (Settings)

**Description** — содержимое файла [`modrinth-description.md`](modrinth-description.md) целиком: английская часть, ниже русская.

**Icon** — `launcher/build/discord-logo.png` (1024×1024, 124 КБ): тот же логотип, что в Discord и у лаунчера.

**Tags**:

- главные (до трёх): `Utility`, `Optimization`, `Social`;
- дополнительный: `Management`.

**Environment**: Client-side — `Required`, Server-side — `Unsupported`. На Modrinth это «Client-side only»: на сервер мод ставить не нужно.

**License**: `MIT`. Файл `LICENSE` уже лежит в проекте и внутри jar.

**Links** (Issue tracker, Source code, Wiki, Discord) — необязательны. Оставьте пустыми, если у мода пока нет GitHub или своего Discord-сервера.

## 3. Галерея (Gallery)

Картинки — из папки `screenshots`. Первую отметьте **Featured**: она станет обложкой проекта.

| Файл | Title | Description |
|---|---|---|
| `01-main-menu.jpg` (Featured) | Main menu | The Aurora main menu with the live northern lights background |
| `02-mod-menu.jpg` | Mod menu | Categories, search and a card for every module |
| `03-settings-color-picker.jpg` | Module settings | Sliders, switches and the colour picker |
| `04-hud-editor.jpg` | HUD editor | Drag, snap and scale every HUD element |
| `08-target-hud-mob-head.jpg` | Target HUD | A live 3D head of any mob and health with a damage trail |
| `06-hud-glass-hotbar.jpg` | HUD and hotbar | HUD elements and the glass hotbar |
| `11-chat-scoreboard-hitboxes.jpg` | Chat, scoreboard and hitboxes | The restyled chat, a movable scoreboard and hitboxes |
| `12-tab-list.jpg` | Player list | Player heads and ping in milliseconds |
| `07-binds.jpg` | Binds | Every key in one place, conflicts highlighted |
| `05-china-hat-jump-circle.jpg` | Cosmetics | China hat and jump circles |
| `13-pause-menu.jpg` | Pause menu | World, server and session info next to your player |
| `09-background-sunset.jpg` | Sunset background | One of the animated menu backgrounds |
| `10-background-synthwave.jpg` | Synthwave background | Another animated menu background |
| `14-accounts.jpg` | Accounts | Switch accounts without restarting the game |

Скриншоты лаунчера (15–19) на страницу мода не нужны. Новые функции на скриншотах пока не показаны; стоит доснять в игре:

- вкладку «Конфиги»;
- настройки прицела с предпросмотром;
- «Быструю настройку» оптимизации;
- статус в профиле Discord.

## 4. Версия (Versions → Create a version)

**File:** `build/libs/aurora-1.0.0+26.3.jar`

**Version title**

```
Aurora 1.0.0
```

**Version number** — как у мода в jar: лаунчер сравнивает часть до `+`.

```
1.0.0+26.3
```

**Release channel:** `Release`. Лаунчер ставит `Release` и `Beta`, но не `Alpha`.

**Loaders:** `Fabric`

**Game versions:** `26.3`

**Dependencies:** `Fabric API` — `Required`.

**Changelog**

```
First public release of Aurora for Minecraft 26.3.

- Mod menu with 8 themes, instant search and a HUD editor with snapping
- 20 HUD elements, including an item counter and FPS with 1% low and a frame time graph
- Crosshair editor with a live first-person preview
- Profiles: PvP, Survival, Builder, Minimal and Streaming, share codes and a profile per server
- Render optimizer with one-click presets for weak PCs
- Mouse Tweaks, screenshots to the clipboard, Discord status, command binds, toggle sprint
- Animated main menu, new pause menu and loading screens, in-game account switcher
- English and Russian

Первый публичный выпуск Aurora для Minecraft 26.3: меню модов с 8 темами и редактором HUD, 20 элементов HUD, редактор прицела с предпросмотром, конфиги с автосменой по серверу, оптимизация FPS, Mouse Tweaks, скриншоты в буфер обмена и статус в Discord.
```

## 5. Отправка на проверку

Новый проект на Modrinth сначала проверяют модераторы: кнопка **Submit for review**, обычно от нескольких часов до пары дней. Описание уже соответствует их требованиям:

- есть английский текст;
- понятно, что делает мод;
- нет ссылок на сторонние загрузки;
- указаны авторы Mouse Tweaks и шрифта Inter.

## 6. Следующие версии

1. Поднимите `mod_version` в `gradle.properties` (например `1.0.1`) и выполните `./gradlew build`.
2. Загрузите новый jar как новую версию: Version number `1.0.1+26.3`, свой changelog, остальное как выше.
3. Aurora Launcher найдёт её сам (раз в час и перед каждым запуском) и поставит игрокам. Новый установщик лаунчера для этого не нужен.

---

## CurseForge

На [curseforge.com](https://www.curseforge.com) → Create project поля почти те же:

- **Project name:** `Aurora Client`.
- **Summary:** тот же английский Summary.
- **Description:** переключите редактор на Markdown и вставьте `modrinth-description.md`.
- **Logo:** `launcher/build/discord-logo.png`.
- **Main category:** `Utility & QoL`; дополнительные: `Cosmetic`, `Map and Information`.
- **License:** `MIT`.

При загрузке файла:

- **Release type:** `Release`;
- **Modloader:** `Fabric`;
- **Environment:** `Client`;
- **Game version:** `26.3`, Java `25`;
- **Related projects:** `Fabric API` — `Required Dependency`;
- **Changelog:** как выше.

Лаунчер берёт обновления только с Modrinth, так что на CurseForge выкладывайте те же версии для игроков, которые ставят моды оттуда.
