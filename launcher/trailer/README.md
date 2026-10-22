# Трейлер Aurora Launcher

Трейлер собирается целиком из кода: настоящий лаунчер снимается вне экрана со сценарием и курсором, голос и музыка синтезируются, кадры монтируются в Python. Готовое видео — `out/aurora-trailer.mp4` (1920×1080, 60 кадров/с, H.264 + AAC, 64 секунды).

## Что в нём

| Время | Что происходит |
|---|---|
| 0–8 с | Лаунчер проявляется из размытия, робот-голос: «Welcome to Aurora», камера наезжает на «Welcome to Aurora» на главной, титульная карточка AURORA LAUNCHER |
| 8–16 с | Дроп. «One click to play»: клик по «Играть», загрузка игры, запуск |
| 16–20 с | «Every server, live»: живой пинг серверов и вход на сервер |
| 20–28 с | «Mods · Resource packs · Shaders»: поиск в каталоге Modrinth, ресурспаки, шейдеры |
| 28–36 с | «Your skin, your style»: рисование на 3D-скине и галерея |
| 36–42 с | «Shortcuts & icons»: ярлык на рабочем столе и иконки из текстур Minecraft |
| 42–46 с | «Discord status»: карточка статуса в Discord |
| 46–56 с | «Aurora Client»: нарезка внутриигровых скриншотов мода |
| 56–64 с | Логотип, «AURORA», голос: «Aurora.» |

Музыка (синтвейв, 120 BPM, ля минор) написана кодом в `audio.py`: все склейки стоят на тактах, на каждой — свуш и вспышка, клики курсора слышны.

## Пересборка

Нужны Node.js (лаунчер), Python 3 с `numpy`, `Pillow` и `imageio-ffmpeg` (из него берётся ffmpeg) и голос Microsoft Zira в Windows.

1. Съёмка лаунчера (из папки `launcher`), около трёх минут:

   ```bash
   AURORA_SCRIPT=trailer/director.js AURORA_HIDDEN=1 npx electron .
   ```

   `AURORA_TRAILER_SCENES=play,servers` переснимает только нужные сцены. Запуск игры в съёмке подменяется: кнопки «Играть» показывают загрузку и запуск, но Minecraft не стартует.

2. Голос:

   ```bash
   powershell -File trailer/voice.ps1 -Text "Welcome to Aurora" -Out trailer/out/audio/raw-welcome.wav
   powershell -File trailer/voice.ps1 -Text "Aurora." -Out trailer/out/audio/raw-aurora.wav
   ```

3. Звук: `python trailer/audio.py` → `out/audio/trailer.wav`.
4. Видео: `python trailer/compose.py` → `out/aurora-trailer.mp4`. Отдельные кадры для проверки: `python trailer/compose.py --stills 3.6 12.5`.

Монтаж — `timeline.py`: время каждой сцены, куски записи, титры и ключи камеры (зум и точка фокуса в координатах окна 1280×780). Сценарий съёмки — `director.js`.

## Превью

`python trailer/thumbnail.py` → `out/thumbnail.jpg` (английская) и `out/thumbnail-ru.jpg` (русская), 1920×1080, около 0,5 МБ — подходят для YouTube (лимит 2 МБ). Кадры лаунчера и сияния берутся из той же съёмки (`out/footage`), машущий игрок рисуется в 3D по скину `thumbnail-skin.png` (`skin3d.py`, классические руки). Раскладка — константы в начале `thumbnail.py`: какие кадры взять, где стоят и как повёрнуты окна, поза и размер игрока.
