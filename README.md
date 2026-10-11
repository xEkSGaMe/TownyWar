# TownyWar

**TownyWar** — серверный плагин для Paper 1.21, добавляющий Towny полноценную систему войн между городами: объявление войны, боевые окна по расписанию, захват чанков флагами, штурмовой TNT, военный магазин, переговоры и дань после победы, подсветку сторон и статистику участников.

- **Автор:** EksGame
- **Версия:** 1.0.0-SNAPSHOT
- **Платформа:** Paper API 1.21 (api-version `1.21`)
- **Java:** 21
- **Сборка:** Maven

## Возможности

- ⚔️ **Войны между городами** — объявление через `/t war <город>` (только мэр), отмена, принудительное завершение админом.
- 🕒 **Боевые окна** — сражения доступны только в заданное конфигурацией время (по умолчанию 16:00–20:30, таймзона настраивается).
- 🚩 **Захват чанков флагами** — установка флага на чанках противника, перехват чанков при удержании, прочность флага, лимит одновременных флагов на город.
- 💣 **Штурмовой TNT** — крафт по рецепту, размещение в чанках врага, собственный фитиль, лимит на чанк, запрет ванильного TNT во время боя.
- 🛒 **Военный магазин (`/t warshop`)** — усиления и тактики за очки войны (Вдохновение, Перемирие, Наёмники) с ценами, кулдаунами и длительностью.
- 🤝 **Переговоры после победы** — окно переговоров (`/twar negotiation` / меню), наложение дани.
- 💰 **Дань** — автоматическое списание процента с города-проигравшего по расписанию.
- ✨ **Подсветка сторон (`/t war glow`)** — свечение игроков враждующих сторон в радиусе.
- 🔒 **Блокировка действий во время войны** — запрещённые подкоманды Towny (`claim`, `unclaim`, `withdraw` и т.д.), блокировка взаимодействия/команд вне боевых правил, защита висячих объектов и стойок.
- 📊 **Статистика войн** — вклад в бой, смерть мэра/жителя как очки, меню статистики (`WarStatsMenu`), список активных войн (`/t warlist`).
- 🏷️ **PlaceholderAPI** — расширение с идентификатором `townywar` (очки и бонусы городов).
- 🗄️ **MySQL** — хранение войн, флагов, настроек городов, журнала войн, статы и дань (HikariCP, все зависимости shade'ятся и reloc-уются).

## Требования

**Обязательно:**
- Paper/Spigot 1.21+ (рекомендуется Paper)
- Java 21
- [Towny](https://github.com/TownyAdvanced/Towny) 0.103.x
- MySQL/MariaDB сервер

**Опционально:**
- Vault (VaultUnlocked) — экономика
- PlaceholderAPI — плейсхолдеры
- EssentialsX — интеграция
- ProtocolLib — поддержка PacketEvents (встроен в дистрибутив)

## Сборка

```bash
mvn clean package
```

Готовый jar появится в `target/TownyWar-1.0.0-SNAPSHOT.jar` (все библиотеки — HikariCP, PacketEvents, PixelWidth — упакованы через maven-shade-plugin с relocation в `ru.example.townywar.libs.*`).

## Установка

1. Положите собранный jar в папку `plugins/` сервера Paper.
2. Убедитесь, что установлены Towny (и опционально Vault/PlaceholderAPI).
3. Создайте базу данных MySQL, например:
   ```sql
   CREATE DATABASE townywar CHARACTER SET utf8mb4;
   ```
4. Запустите сервер — будут созданы `config.yml` и таблицы БД.
5. Настройте доступ к БД в `plugins/TownyWar/config.yml` (`database.host`, `port`, `name`, `user`, `password`).

## Конфигурация

Основной файл — `plugins/TownyWar/config.yml`. Ключевые секции:

| Секция | Назначение |
|---|---|
| `war.declare` | Правила объявления войны (минимальный возраст города) |
| `war.preparation-hours` | Время подготовки перед боевым окном |
| `war.combat.window` | Боевое окно и таймзона (по умолчанию `Europe/Moscow`, 16:00–20:30) |
| `war.points` | Стартовые очки, лимиты, очки за смерти мэра/жителя |
| `war.capture` | Прочность флага, время захвата чанка, диапазон Y, лимит флагов |
| `war.surrender` | Прочность флага капитуляции и её длительность |
| `war.assault-tnt` | Прочность, фитиль, лимит на чанк, мощность взрыва |
| `war.negotiation` | Длительность окна переговоров |
| `war.tribute` | Процент дани и время списания |
| `war.glow` | Радиус подсветки сторон |
| `war.blocked-commands` | Подкоманды Towny, запрещённые во время войны |
| `warshop.*` | Цены, кулдауны и эффекты товаров военного магазина |
| `database.*` | Подключение к MySQL и настройки пула соединений |
| `messages.*` | Сообщения игрокам (MiniMessage) |

## Команды

### Игровые (через интерцептор Towny `/t`)

| Команда | Описание | Право |
|---|---|---|
| `/t war <город>` | Объявить войну городу | `townywar.declare` (мэр) |
| `/t war cancel` | Отменить войну | `townywar.cancel` |
| `/t war glow on/off` | Подсветка сторон конфликта | `townywar.glow` |
| `/t warlist` | Список активных войн (меню) | `townywar.list` |
| `/t warshop` | Военный магазин (только в фазе COMBAT) | `townywar.warshop` |

### Административные (`/twar`)

| Команда | Описание |
|---|---|
| `/twar reload` | Перечитать конфигурацию и сообщения |
| `/twar help` | Справка по командам |
| `/twar admin addpoints <город> <n>` | Начислить очки войны |
| `/twar admin removepoints <город> <n>` | Снять очки войны |
| `/twar admin setpoints <город> <n>` | Установить очки |
| `/twar admin setmaxflags <город> <n>` | Лимит одновременных флагов |
| `/twar admin setbonus <город> <delta>` / `getbonus <город>` | Управление стартовым бонусом |
| `/twar admin declare <атакующий> <защитник>` | Принудительно объявить войну |
| `/twar admin cancel <город>` | Отменить войну |
| `/twar admin end <город> [причина]` | Завершить войну |
| `/twar admin phase <город> <PREPARATION\|COMBAT\|NEGOTIATION>` | Сменить фазу войны |
| `/twar admin info <город>` | Информация о войне города |
| `/twar admin list` | Список всех войн |

## Права доступа (permissions)

| Permission | По умолчанию | Описание |
|---|---|---|
| `townywar.admin` | op | Доступ к `/twar` (admin, reload) |
| `townywar.declare` | op | Объявление войны |
| `townywar.cancel` | op | Отмена войны |
| `townywar.glow` | op | Подсветка сторон |
| `townywar.list` | все | Список войн |
| `townywar.negotiation` | op | Переговоры после победы |
| `townywar.breakblocks` | false | Ломать блоки во вражеских чанках во время боя |
| `townywar.placeblocks` | false | Ставить блоки во вражеских чанках во время боя |
| `townywar.warshop` | false | Доступ к `/t warshop` |

## Плейсхолдеры (PlaceholderAPI)

Префикс `%townywar_...%`:

- `%townywar_default_points%` — configured default points
- `%townywar_starting_points_<город>%` — стартовые очки города
- `%townywar_starting_bonus_<город>%` — стартовый бонус города
- `%townywar_player_starting_points%` / `%townywar_player_starting_bonus%` — значения города игрока

## Структура проекта

```
src/main/java/ru/example/townywar/
├── TownyWarPlugin.java        # Основной класс плагина (Paper plugin)
├── TownyWarBootstrap.java     # Bootstrapper
├── assault/                   # Штурмовой TNT: менеджер, предмет, рецепт, репозиторий
├── command/                   # /twar, интерцептор /t war, регистрация Brigadier-команд
├── config/                    # ConfigManager, MessagesManager
├── database/                  # Database (HikariCP), репозитории войн/настроек/журнала
├── flag/                      # Флаги: установка, отображение, захват чанков, рецепты
├── glow/                      # Подсветка сторон конфликта
├── integration/               # Hooks: Vault-экономика, Towny, PlaceholderAPI
├── listener/                  # Слушатели: PvP, TNT, команды, взаимодействия и др.
├── menu/                      # GUI: статистика войны, список войн
├── negotiation/               # Переговоры, дань (менеджер, меню, планировщик)
├── stats/                     # Статистика игроков за войну
├── util/                      # Утилиты
├── war/                       # Ядро: War, WarManager, фазы, стороны, bossbar, боевое окно
└── warshop/                   # Военный магазин: товары, меню, репозиторий
src/main/resources/
├── paper-plugin.yml           # Метаданные плагина, зависимости, permissions
└── config.yml                 # Конфигурация по умолчанию
```

## Лицензия

Проприетарное ПО для внутреннего использования (© EksGame).
