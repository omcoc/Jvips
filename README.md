# JVIPS — VIP Management System for Hytale

## Want to help with translation? Send the file to: [gorgueirajc@gmail.com](mailto:gorgueirajc@gmail.com)

## <span style="color:#e03e2d">Help JPlugins grow, make a donation:</span> <span style="color:#f1c40f"><strong><a href="https://www.paypal.com/donate/?hosted_button_id=8WXQWXBWEC7P2" target="_blank" rel="nofollow">PayPal</a></strong></span>

JVIPS is a **complete and production-ready VIP management system** for Hytale servers, built around **secure vouchers**, **automatic expiration**, **persistent virtual chests**, and **server-side command execution**.

It allows server owners to **sell, distribute, or reward VIP access safely**, with full integration to permission plugins such as **LuckPerms**, while maintaining strict validation and anti-abuse protections.

***

## ✨ Core Features

### 🎟️ Secure VIP Vouchers

*   Cryptographically bound to a specific player (UUID)
*   HMAC signature validation prevents tampering or duplication
*   Vouchers cannot be dropped, reused, or transferred
*   Consumed only after successful activation

### 🎯 Command Vouchers — **NEW**

Lightweight, single-use vouchers that **only execute commands** — no VIP status, no duration, no data persistence. Perfect for quests, teleports, rewards, kits, or any server action triggered by an item.

*   Configured in a **separate file** (`command_vouchers.json`)
*   Supports **any item ID** — not limited to `Jvips_Voucher`
*   UUID-bound to the receiving player (non-transferable)
*   **No HMAC** — simplified system, no cryptographic overhead
*   **No players.json** — nothing is recorded, pure fire-and-forget
*   **Works even with an active VIP** — never blocked by existing VIP state
*   Drop-blocked just like VIP vouchers
*   Delivered via `/vips givecmd <id> <player>`

### 🔄 Voucher Stacking

Extend active VIP time by using additional vouchers of the same type:

*   Enable per-VIP with `"stackable": true` in `vips.json`
*   Configure a maximum stack count with `"stackAmount"` (0 = unlimited)
*   Each stack adds the voucher's duration to the current expiration
*   Works with both voucher activation and admin commands
*   Stack count tracked per player

### ⏱️ Custom Duration

Admins can issue vouchers or grant VIPs with a custom duration instead of the default from `vips.json`:

*   `/vips givekey <vip> <player> --duration 1d2h10m`
*   `/vips add <player> <vip> --duration 30d`
*   Supports `d` (days), `h` (hours), `m` (minutes), `s` (seconds)
*   Custom duration is embedded in the voucher's HMAC signature (tamper-proof)
*   Voucher lore reflects the custom duration

### ⏳ Automatic VIP Lifecycle

*   VIPs expire automatically without requiring player login
*   Configurable commands on activation and expiration
*   Sequential command execution (order guaranteed)
*   Background ticking system handles all timers

### 📦 Virtual Chest (`/vips chest`)

A persistent, permission-based virtual chest using the native Hytale container GUI. VIP players can securely store items that survive server restarts, world changes, and even VIP expiration.

*   **Native GUI** — Identical to a regular Hytale chest (drag & drop)
*   **Auto-save** — Items saved automatically when the chest is closed
*   **Permission-based capacity** — Chest size scales with VIP tier
*   **Smart downgrade** — Excess items are dropped at the player's feet when capacity decreases (never silently deleted)
*   **Persistent storage** — Items survive VIP expiration and are restored when renewed

| Permission     | Rows | Slots    |
| -------------- | ---- | -------- |
| `jvips.chest.9` | 1    | 9        |
| `jvips.chest.18` | 2    | 18       |
| `jvips.chest.27` | 3    | 27       |
| `jvips.chest.36` | 4    | 36       |
| `jvips.chest.45` | 5    | 45       |
| `jvips.chest.54` | 6    | 54 (max) |

### 📋 VIP History & Status

*   `/vips status` — View your active VIPs with remaining time
*   `/vips list <player>` — View any player's VIP status (admin)
*   `/vips history <player>` — Full activation/expiration history with timestamps
*   Paginated output for large histories

### 🔧 Smart Config Merge

Update the plugin without losing your configuration:

*   New config properties are automatically added with default values
*   Existing values are **never overwritten** — your customizations are preserved
*   New VIP properties (e.g., `stackable`, `stackAmount`) are injected into all existing VIPs
*   New message keys are merged into all language files
*   Arrays like `commandsOnActivate` and `commandsOnExpire` are never touched
*   Works on `config.json`, `vips.json`, and all `Messages/*.json` files
*   Runs on startup and on `/vips reload`

### 🌍 Multi-Language Support

*   Fully configurable messages via JSON
*   Ships with `en_US`, `pt_BR`, and `es_ES`
*   Language selection via `config.json`
*   All player-facing messages are customizable

### 📢 VIP Broadcast System

*   Global EventTitle broadcast on VIP activation
*   Cooldown and grouping support for multiple activations
*   Plain-text optimized for Hytale UI stability
*   Fully customizable via language files

***

## 🧾 Commands

### Player Commands

| Command        | Description                   | Permission           |
| -------------- | ----------------------------- | -------------------- |
| `/vips chest`  | Opens your VIP virtual chest  | `jvips.chest.<slots>` |
| `/vips status` | Shows your active VIPs        | `jvips.use`          |

### Admin Commands

| Command                                         | Description                                    | Permission   |
| ------------------------------------------------ | ---------------------------------------------- | ------------ |
| `/vips givekey <vip> <player> [--duration time]` | Gives a VIP voucher (optional custom duration) | `jvips.admin` |
| `/vips givecmd <id> <player>`                    | Gives a Command Voucher                        | `jvips.admin` |
| `/vips add <player> <vip> [--duration time]`     | Grants VIP directly (optional custom duration) | `jvips.admin` |
| `/vips remove <player> <vip>`                    | Removes VIP immediately                        | `jvips.admin` |
| `/vips list [--player name] [--page N]`          | Shows active VIPs                              | `jvips.admin` |
| `/vips history [--player name] [--page N]`       | Shows full VIP history                         | `jvips.admin` |
| `/vips reload`                                   | Reloads configuration and messages             | `jvips.admin` |

### Duration Format

The `--duration` flag accepts a compact time string:

| Example       | Result                           |
| ------------- | -------------------------------- |
| `30d`         | 30 days                          |
| `1d2h10m`     | 1 day, 2 hours, 10 minutes      |
| `2h30m`       | 2 hours, 30 minutes             |
| `10m`         | 10 minutes                       |
| `1d2h10m5s`   | 1 day, 2 hours, 10 min, 5 sec   |

***

## 🔐 Permissions

| Permission              | Description                                             |
| ----------------------- | ------------------------------------------------------- |
| `jvips.use`             | Allows using VIP vouchers, command vouchers, and viewing status |
| `jvips.admin`           | Allows all admin commands                               |
| `jvips.chest.<slots>`   | Grants virtual chest access (9, 18, 27, 36, 45, or 54) |

***

## 📁 Configuration

### `config.json`

```json
{
  "language": "en_US",
  "vipExpiry": {
    "sweepEverySeconds": 10
  },
  "vipBroadcast": {
    "enabled": true,
    "cooldownSeconds": 30
  },
  "formatting": {
    "dateFormat": "dd/MM/yyyy",
    "hourFormat": "24h",
    "timezone": "America/Sao_Paulo"
  },
  "listSettings": {
    "entriesPerPage": 5
  },
  "logging": {
    "debug": false
  }
}
```

### `vips.json`

```json
{
  "security": {
    "hmacSecret": "YOUR_LONG_RANDOM_SECRET_HERE"
  },
  "vips": {
    "thorium": {
      "displayName": "[THORIUM]",
      "durationSeconds": 2592000,
      "stackable": true,
      "stackAmount": 3,
      "voucher": {
        "itemId": "Jvips_Voucher",
        "name": "[THORIUM] Voucher #{voucherIdShort}",
        "lore": [
          "Activates: [THORIUM]",
          "Duration: {durationHuman}",
          "Bound to: {player}",
          "Right click to activate"
        ]
      },
      "commandsOnActivate": [
        "say [JVIPS] VIP Thorium activated!",
        "lp user {player} parent add thorium",
        "lp user {player} parent switchprimarygroup thorium"
      ],
      "commandsOnExpire": [
        "say [JVIPS] VIP Thorium expired.",
        "lp user {player} parent switchprimarygroup default",
        "lp user {player} parent remove thorium"
      ]
    }
  }
}
```

#### Stacking Configuration

| Property       | Type    | Default | Description                                      |
| -------------- | ------- | ------- | ------------------------------------------------ |
| `stackable`    | boolean | `false` | Allow extending VIP time with same-type vouchers |
| `stackAmount`  | int     | `0`     | Max extensions allowed (0 = unlimited)           |

When `stackable` is `true`, using another voucher of the same VIP type **adds** the duration to the current expiration instead of being blocked. The `stackAmount` limits how many times a player can extend.

### `command_vouchers.json` — **NEW**

Separate configuration file for Command Vouchers. Each entry defines a single-use voucher that executes commands without creating VIP status.

```json
{
  "hunterquest": {
    "displayName": "Hunter Quest",
    "voucher": {
      "itemId": "Jvips_Voucher2",
      "name": "Quest Hunt Voucher #{voucherIdShort}",
      "lore": [
        "Quest: [HUNTER]",
        "Bound to: {player}",
        "Right click to start"
      ]
    },
    "commandsOnActivate": [
      "tp {player} hunterquest",
      "tellraw {player} [JVIPS] You started the Hunter Quest adventure!"
    ]
  },
  "starterkit": {
    "displayName": "Starter Kit",
    "voucher": {
      "itemId": "Jvips_Voucher",
      "name": "Starter Kit #{voucherIdShort}",
      "lore": [
        "Kit: [STARTER]",
        "Bound to: {player}",
        "Right click to claim"
      ]
    },
    "commandsOnActivate": [
      "kit give {player} starter",
      "tellraw {player} [JVIPS] Starter kit delivered!"
    ]
  }
}
```

#### Command Voucher Fields

| Field                | Type     | Required | Description                                          |
| -------------------- | -------- | -------- | ---------------------------------------------------- |
| `displayName`        | string   | Yes      | Display name shown in messages                       |
| `voucher.itemId`     | string   | No       | Any game item ID (default: `Jvips_Voucher`)          |
| `voucher.name`       | string   | No       | Item display name (`{player}`, `{voucherIdShort}`)   |
| `voucher.lore`       | string[] | No       | Item lore lines (`{player}`, `{voucherIdShort}`)     |
| `commandsOnActivate` | string[] | Yes      | Commands to run on use (`{player}` replaced)         |

#### VIP Voucher vs Command Voucher

| Feature                   | VIP Voucher            | Command Voucher           |
| ------------------------- | ---------------------- | ------------------------- |
| Config file               | `vips.json`            | `command_vouchers.json`   |
| Admin command              | `/vips givekey`        | `/vips givecmd`           |
| HMAC security             | ✅ Yes                  | ❌ No                      |
| Duration / Expiry         | ✅ Yes                  | ❌ No                      |
| Saved in `players.json`   | ✅ Yes                  | ❌ No                      |
| Saved in history          | ✅ Yes                  | ❌ No                      |
| Blocked by active VIP     | ✅ Yes (unless stacked) | ❌ Never blocked           |
| Custom item ID            | ✅ Yes                  | ✅ Yes                     |
| UUID-bound                | ✅ Yes                  | ✅ Yes                     |
| Drop-blocked              | ✅ Yes                  | ✅ Yes                     |
| Runs commands on activate | ✅ Yes                  | ✅ Yes                     |

### Messages (`en_US.json`, `pt_BR.json`, `es_ES.json`)

```json
{
  "player.vipActivated": "Your VIP {vipDisplay} has been activated!",
  "player.vipStacked": "VIP extended! {vipDisplay} +{addedDuration} (total remaining: {totalRemaining}, stacks: {stackCount}).",
  "error.stackLimitReached": "Stack limit reached ({maxStack}). You cannot extend this VIP further.",
  "error.invalidDuration": "Invalid duration: {input}. Use format: 1d 2h 10m 5s",
  "commandvoucher.received": "You received a command voucher: {displayName}",
  "commandvoucher.activated": "Command voucher activated: {displayName}",
  "error.invalidCommandVoucher": "Command voucher not found: {id}",
  "admin.givecmd.ok": "Command voucher {displayName} given to {player}.",
  "broadcast.vipActivated.title": "{player} ACTIVATED VIP",
  "broadcast.vipActivated.subtitle": "[{vipPlain}]",
  "chest.noPermission": "You don't have permission to use the virtual chest.",
  "chest.opened": "Virtual chest opened."
}
```

***

## 📦 Data Storage

| File                     | Description                                              |
| ------------------------ | -------------------------------------------------------- |
| `data/players.json`      | Player VIP states, activation history, stack counts      |
| `data/history.json`      | Full VIP event log (activations, expirations, removals)  |
| `data/vouchers.json`     | Voucher issuance and usage records                       |
| `data/vipschest.json`    | Virtual chest contents (per player, up to 54 slots)      |

All files use atomic writes (`.tmp` + move) to prevent corruption on crash.

> **Note:** Command Vouchers do **not** write to any data file. They are purely fire-and-forget.

***

## ⏱️ VIP Expiration

*   VIPs expire automatically via a background ticking system
*   Expired VIPs execute `commandsOnExpire` commands
*   Player does **not** need to be online for expiration
*   Expired VIPs are removed from the data store and logged in history

***

## 🧩 Dependencies

*   **LuckPerms** (recommended for permissions)
*   **Hytale Server API**

***

## 📦 Installation

1.  Drop `JVIPS.jar` into `/mods`
2.  Start or restart the server
3.  Configure `config.json`, `vips.json`, `command_vouchers.json`, and language files
4.  Assign permissions via LuckPerms
5.  Done 🎉

***

## 📜 Release Notes

### v1.3.0 — Command Vouchers

**Command Voucher System**

*   New voucher type: **Command Vouchers** — single-use items that only execute commands
*   Separate config file: `command_vouchers.json` (independent from `vips.json`)
*   No HMAC, no duration, no VIP state — pure fire-and-forget
*   **Never blocked by active VIP** — works regardless of player's VIP status
*   **No data persistence** — nothing written to `players.json` or `history.json`
*   UUID-bound to the player (non-transferable, same as VIP vouchers)
*   Drop-blocked (cannot be thrown on the ground)
*   Supports **any item ID** — use custom items for different voucher visuals
*   `{player}` placeholder replaced in all commands
*   New admin command: `/vips givecmd <id> <player>`
*   New messages: `commandvoucher.received`, `commandvoucher.activated`, `error.invalidCommandVoucher`, `admin.givecmd.ok`
*   Fully localized in `en_US`, `pt_BR`, and `es_ES`
*   Reloaded automatically via `/vips reload`

**Use Cases**

*   Quest starters (teleport player to quest area)
*   Kit delivery (give items via commands)
*   Event access (run permission commands)
*   Reward coupons (execute any server command)
*   Teleport tokens (tp to specific locations)

### v1.2.0 — Stacking, Custom Duration & Smart Config Merge

**Voucher Stacking**

*   New `stackable` and `stackAmount` per-VIP configuration
*   Use additional vouchers of the same type to extend active VIP time
*   Stack count tracked per player in `players.json`
*   Works with both voucher activation (`Right Click`) and `/vips add`
*   Dedicated messages: `player.vipStacked`, `error.stackLimitReached`

**Custom Duration**

*   New `--duration` flag for `/vips givekey` and `/vips add`
*   Compact format: `1d2h10m5s` (days, hours, minutes, seconds)
*   Custom duration embedded in voucher's HMAC signature (tamper-proof)
*   Voucher lore and admin messages reflect the effective duration

**Smart Config Merge**

*   Automatic merge of new configuration properties on plugin update
*   `config.json`: new sections and fields added without overwriting existing values
*   `vips.json`: new VIP properties (e.g., `stackable`) injected into all existing VIPs
*   `Messages/*.json`: new message keys added to all language files
*   Arrays (`commandsOnActivate`, `commandsOnExpire`, `lore`) are never modified
*   Runs on startup and `/vips reload`
*   All merge actions logged to console for transparency

### v1.1.0 — Virtual Chest & History

**Virtual Chest**

*   New `/vips chest` command with native Hytale container GUI
*   Permission-based capacity (`jvips.chest.9` through `jvips.chest.54`)
*   Full item persistence (ID, quantity, durability, metadata)
*   Smart downgrade: excess items dropped at player's feet via ECS item spawning
*   Atomic JSON writes prevent data loss

**History & Status**

*   New `/vips status` command for players
*   New `/vips list <player>` and `/vips history <player>` for admins
*   Paginated output for large histories
*   Full event log with timestamps

**Multilingual**

*   Added `es_ES` (Spanish) language file
*   All new features fully localized in `en_US`, `pt_BR`, and `es_ES`

### v1.0.3 — Initial Release

**Core**

*   Secure voucher system with HMAC validation
*   Player-bound vouchers (UUID)
*   Persistent VIP storage

**Automation**

*   Automatic VIP expiration via background ticking
*   Configurable commands on activation and expiration

**Assets**

*   Embedded asset pack with custom voucher items

**Security**

*   Voucher replay protection
*   Drop-blocking for VIP vouchers
*   Atomic file writes for all persistent data

***

## 🔒 Security & Anti-Abuse

### ✅ Implemented

*   HMAC signature validation on all VIP vouchers
*   UUID-bound vouchers (non-transferable) — both VIP and Command types
*   Server-side validation only
*   Sequential command execution
*   Voucher drop-blocking system (VIP + Command vouchers)
*   Atomic file writes for all persistent data
*   Custom duration included in HMAC signature (prevents tampering)
*   Command Vouchers isolated from VIP state (no privilege escalation)

### 🔜 Planned

*   Cooldown per player for activation attempts
*   Max active vouchers per player
*   Admin audit log
*   Webhook support (Discord, APIs)

***

## 📜 License

MIT License
