<div align="center">

# ⚔️ TownyWar

**Полноценная система войн между городами для Paper + Towny**

[![Paper](https://img.shields.io/badge/Paper-1.21+-E98B35?style=flat-square&logo=minecraft&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Towny](https://img.shields.io/badge/Towny-0.103.x-4b8bbe?style=flat-square)](https://github.com/TownyAdvanced/Towny)
[![Maven](https://img.shields.io/badge/build-maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white)](https://maven.apache.org)
[![License](https://img.shields.io/badge/license-Proprietary-lightgrey?style=flat-square)](#-лицензия)

*Объявляйте войны, захватывайте чанки флагами, штурмуйте врагов TNT,<br/>
торгуйтесь после победы и взыскивайте дань — всё в одном плагине.*

</div>

---

## 📑 Содержание

- [✨ Возможности](#-возможности)
- [🔄 Жизненный цикл войны](#-жизненный-цикл-войны)
- [📋 Требования](#-требования)
- [🔨 Сборка](#-сборка)
- [🚀 Установка](#-установка)
- [⚙️ Конфигурация](#️-конфигурация)
- [🎮 Команды](#-команды)
- [🔑 Права доступа](#-права-доступа)
- [🏷️ Плейсхолдеры](#️-плейсхолдеры)
- [🧩 API для разработчиков](#-api-для-разработчиков)
- [📁 Структура проекта](#-структура-проекта)
- [📄 Лицензия](#-лицензия)

---

## ✨ Возможности

| | Функция | Описание |
|---|---|---|
| ⚔️ | **Войны между городами** | Объявление через `/t war <город>` (только мэр), отмена, принудительное завершение админом |
| 🕒 | **Боевые окна** | Сражения доступны только в заданное время (по умолчанию 16:00–20:30, таймзона настраивается) |
| 🚩 | **Захват чанков флагами** | Установка флага на чанках противника, перехват при удержании, прочность флага, лимит флагов |
| 💣 | **Штурмовой TNT** | Крафт по рецепту, размещение в чанках врага, собственный фитиль, лимит на чанк, запрет ванильного TNT |
| 🛒 | **Военный магазин** | `/t warshop` — усиления и тактики за очки войны (Вдохновение, Перемирие, Наёмники) |
| 🤝 | **Переговоры** | Окно переговоров после победы (`/twar negotiation` / GUI-меню) с наложением дани |
| 💰 | **Дань** | Автоматическое списание процента с проигравшего города по расписанию |
| ✨ | **Подсветка сторон** | `/t war glow` — свечение игроков враждующих фракций в радиусе |
| 🔒 | **Блокировка действий** | Запрещённые подкоманды Towny, блокировка взаимодействия вне боевых правил, защита висячих объектов и стоек |
| 📊 | **Статистика войн** | Вклад в бой, очки за смерти мэра/жителей, GUI статистики, список активных войн |
| 🗄️ | **MySQL** | Хранение войн, флагов, настроек, журнала и статы (HikariCP, зависимости shade'ятся) |

### 🧩 Интеграции

**PlaceholderAPI** · **Vault (VaultUnlocked)** · **ProtocolLib / PacketEvents** · **EssentialsX**

---

## 🔄 Жизненный цикл войны

```
   ┌──────────┐      ┌─────────────┐      ┌────────┐      ┌─────────────┐      ┌──────────┐
   │  /t war  │ ───▶ │ PREPARATION │ ───▶ │ COMBAT │ ───▶ │ NEGOTIATION │ ───▶ │  TRIBUTE │
   │ объявление│      │  подготовка │      │ боевое │      │  переговоры │      │  дань до │
   └──────────┘      └─────────────┘      │  окно  │      └─────────────┘      │  победителя
                                          └────────┘                           └──────────┘
```

1. **Объявление** — мэр города атакует другой город командой `/t war <город>`.
2. **Подготовка** — период перед боевым окном: можно крафтить флаги и штурмовой TNT.
3. **COMBAT** — боевое окно: PvP, установка флагов на чанки врага, штурмовые взрывы, работа военного магазина.
4. **NEGOTIATION** — побеждённая сторона может вести переговоры о капитуляции.
5. **Дань** — проигравший город регулярно платит процент победителю.

---

## 📋 Требования

**Обязательно:**

- 🖥️ Paper/Spigot **1.21+** (рекомендуется Paper)
- ☕ Java **21**
- 🏰 [Towny](https://github.com/TownyAdvanced/Towny) **0.103.x**
- 🗄️ MySQL / MariaDB сервер

**Опционально:**

- 💵 [VaultUnlocked](https://github.com/Milk-Bowl/VaultUnlocked) — экономика
- 🏷️ [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) — плейсхолдеры
- 🛠️ EssentialsX — интеграция
- 📡 ProtocolLib — поддержка PacketEvents (встроен в дистрибутив)

---

## 🔨 Сборка

```bash
mvn clean package
```

Готовый jar появится в `target/TownyWar-1.0.0-SNAPSHOT.jar`. Все библиотеки (HikariCP, PacketEvents, PixelWidth) упакованы через `maven-shade-plugin` с relocation в `ru.example.townywar.libs.*`.

---

## 🚀 Установка

> [!IMPORTANT]
> TownyWar требует подключения к MySQL — без настроенной БД плагин не сможет хранить состояние войн.

1. Положите собранный jar в папку `plugins/` сервера Paper.
2. Убедитесь, что установлены Towny (и опционально Vault / PlaceholderAPI).
3. Создайте базу данных:
   ```sql
   CREATE DATABASE townywar CHARACTER SET utf8mb4;
   ```
4. Запустите сервер — будут созданы `config.yml` и таблицы БД.
5. Пропишите доступ к БД в `plugins/TownyWar/config.yml` (`database.host`, `port`, `name`, `user`, `password`).

---

## ⚙️ Конфигурация

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

---

## 🎮 Команды

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
| `/twar admin setbonus <город> <delta>` | Управление стартовым бонусом |
| `/twar admin getbonus <город>` | Получить стартовый бонус |
| `/twar admin declare <атакующий> <защитник>` | Принудительно объявить войну |
| `/twar admin cancel <город>` | Отменить войну |
| `/twar admin end <город> [причина]` | Завершить войну |
| `/twar admin phase <город> <PREPARATION\|COMBAT\|NEGOTIATION>` | Сменить фазу войны |
| `/twar admin info <город>` | Информация о войне города |
| `/twar admin list` | Список всех войн |

---

## 🔑 Права доступа

| Permission | По умолчанию | Описание |
|---|---|---|
| `townywar.admin` | `op` | Доступ к `/twar` (admin, reload) |
| `townywar.declare` | `op` | Объявление войны |
| `townywar.cancel` | `op` | Отмена войны |
| `townywar.glow` | `op` | Подсветка сторон |
| `townywar.list` | `все` | Список войн |
| `townywar.negotiation` | `op` | Переговоры после победы |
| `townywar.breakblocks` | `false` | Ломать блоки во вражеских чанках во время боя |
| `townywar.placeblocks` | `false` | Ставить блоки во вражеских чанках во время боя |
| `townywar.warshop` | `false` | Доступ к `/t warshop` |

---

## 🏷️ Плейсхолдеры

Префикс `%townywar_...%` (требуется PlaceholderAPI):

```text
%townywar_default_points%                     — configured default points
%townywar_starting_points_<город>%            — стартовые очки города
%townywar_starting_bonus_<город>%             — стартовый бонус города
%townywar_player_starting_points%             — значения города игрока
%townywar_player_starting_bonus%              — бонус города игрока
```

---

## 🧩 API для разработчиков

Плагин предоставляет программный API (`ru.example.townywar.api.TownyWarAPI`) и набор событий для сторонних плагинов:

- `WarDeclaredEvent` — объявление войны
- `WarEndedEvent` — завершение войны
- `WarPhaseChangeEvent` — смена фазы войны
- `TownStartingBonusChangeEvent` — изменение стартового бонуса города

📖 **Полное руководство по интеграции** (включая материалы для ИИ-кодогенераторов:
справочник методов, модель данных, готовые рецепты — суммируемый бонус, лимит флагов и др.,
антипаттерны и компактную спеку для промптов): **[docs/API-INTEGRATION-GUIDE.md](docs/API-INTEGRATION-GUIDE.md)**

---

## 📁 Структура проекта

```text
src/main/java/ru/example/townywar/
├── TownyWarPlugin.java        # Основной класс плагина (Paper plugin)
├── TownyWarBootstrap.java     # Bootstrapper
├── api/                       # Программный API и события
├── assault/                   # Штурмовой TNT: менеджер, предмет, рецепт
├── command/                   # /twar, интерцептор /t war, Brigadier-команды
├── config/                    # ConfigManager, MessagesManager
├── database/                  # Database (HikariCP), репозитории
├── flag/                      # Флаги: установка, отображение, захват чанков
├── glow/                      # Подсветка сторон конфликта
├── integration/               # Hooks: Vault, Towny, PlaceholderAPI
├── listener/                  # Слушатели: PvP, TNT, команды и др.
├── menu/                      # GUI: статистика войны, список войн
├── negotiation/               # Переговоры и дань
├── stats/                     # Статистика игроков за войну
├── util/                      # Утилиты
├── war/                       # Ядро: War, WarManager, фазы, bossbar
└── warshop/                   # Военный магазин: товары, меню
src/main/resources/
├── paper-plugin.yml           # Метаданные плагина, зависимости, permissions
└── config.yml                 # Конфигурация по умолчанию
```

---

## 📄 Лицензия

Проприетарное ПО для внутреннего использования © **EksGame**.

---

<div align="center">

**TownyWar** · v1.0.0-SNAPSHOT · Paper 1.21 · Java 21

Сделано для серверов на Towny ⚔️

</div>
