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

### 🎯 Command Vouchers

Lightweight, single-use vouchers that **only execute commands** — no VIP status, no duration lifecycle. Perfect for quests, teleports, rewards, kits, or any server action triggered by an item.

*   Configured in a **separate file** (`command_vouchers.json`)
*   Supports **any item ID** — not limited to `Jvips_Voucher`
*   UUID-bound to the receiving player (non-transferable)
*   **No HMAC** — simplified system, no cryptographic overhead
*   **No players.json / history.json flow for VIP lifecycle**
*   **Works even with an active VIP** — never blocked by existing VIP state
*   Drop-blocked just like VIP vouchers
*   Delivered via `/vips givecmd <id> <player>`
*   Supports command execution mode:
    *   `player_side: true` → executes as player
    *   `player_side: false` → executes as console

### 🔄 Voucher Stacking (Global)

Extend active VIP time by using additional vouchers of the same type:

*   Global toggle in `config.json` with `vipStack.enabled`
*   Global limit in `config.json` with `vipStack.maxStack` (0 = unlimited)
*   Each stack adds voucher duration to current expiration
*   Works with voucher activation and admin commands
*   Stack count tracked per player

### ⏱️ Custom Duration

Admins can issue vouchers or grant VIPs with a custom duration instead of the default from `vips.json`:

*   `/vips givekey <vip> <player> --duration 1d2h10m`
*   `/vips givekeytab <vip> <player> --duration 1d2h10m`
*   `/vips add <player> <vip> --duration 30d`
*   Supports `d` (days), `h` (hours), `m` (minutes), `s` (seconds)
*   Custom duration is embedded in VIP voucher HMAC signature (tamper-proof)
*   Voucher lore reflects custom duration

### 🧩 Vouchers TAB Activation (NEW)

A separate, safe flow to activate VIP vouchers directly from the menu tab:

*   New admin command: `/vips givekeytab <vip> <player> [--duration time]`
*   Voucher is saved directly to TAB storage (`data/tab_vouchers.json`)
*   Click slot in **Vouchers** tab to activate
*   Slot is consumed after successful activation
*   UI refreshes immediately after click (status, stacks, remaining time, slot content)
*   Legacy `/vips givekey` flow remains unchanged (physical item in inventory)

### ⏳ Automatic VIP Lifecycle

*   VIPs expire automatically without requiring player login
*   Configurable commands on activation and expiration
*   Sequential command execution (order guaranteed)
*   Background ticking system handles all timers

### 📦 Virtual Chest (`/vips chest`)

A persistent, permission-based virtual chest using native Hytale container GUI.

*   **Native GUI** — Identical to a regular Hytale chest
*   **Auto-save** — Items saved automatically when chest is closed
*   **Permission-based capacity** — Chest size scales with VIP tier
*   **Smart downgrade** — Excess items are dropped at player's feet when capacity decreases
*   **Persistent storage** — Survives restarts and VIP expiration

| Permission     |Rows |Slots    |
| -------------- |---- |-------- |
| <code>jvips.chest.9</code> |1    |9        |
| <code>jvips.chest.18</code> |2    |18       |
| <code>jvips.chest.27</code> |3    |27       |
| <code>jvips.chest.36</code> |4    |36       |
| <code>jvips.chest.45</code> |5    |45       |
| <code>jvips.chest.54</code> |6    |54 (max) |

### 📋 VIP History & Status

*   `/vips status` — View active VIP with remaining time
*   `/vips list <player>` — View player VIP status (admin)
*   `/vips history <player>` — Activation/expiration history with timestamps
*   Paginated output for large histories

### 🔧 Smart Config Merge

Update plugin without losing customization:

*   New config properties are auto-added
*   Existing values are **never overwritten**
*   New message keys merged into language files
*   Arrays like command lists are never replaced
*   Runs on startup and `/vips reload`

### 🌍 Multi-Language Support

*   Fully configurable messages via JSON
*   Ships with `en_US`, `pt_BR`, and `es_ES`
*   Language selection via `config.json`

### 📢 VIP Broadcast System

*   Global EventTitle broadcast on VIP activation
*   Cooldown and grouping support
*   Plain-text optimized for Hytale UI stability
*   Fully customizable via language files

***

## 🧾 Commands

### Player Commands

| Command      |Description                  |Permission          |
| ------------ |---------------------------- |------------------- |
| <code>/vips menu</code> |Opens VIP menu |<code>jvips.use</code> |
| <code>/vips status</code> |Shows your VIP status |<code>jvips.use</code> |
| <code>/vips chest</code> |Opens your VIP virtual chest |<code>jvips.chest.<slots></code> |

### Admin Commands

| Command                                        |Description                                    |Permission  |
| ---------------------------------------------- |---------------------------------------------- |----------- |
| <code>/vips givekey <vip> <player> [--duration time]</code> |Gives a physical VIP voucher item |<code>jvips.admin</code> |
| <code>/vips givekeytab <vip> <player> [--duration time]</code> |Gives VIP voucher directly to Vouchers TAB |<code>jvips.admin</code> |
| <code>/vips givecmd <id> <player></code> |Gives a Command Voucher                        |<code>jvips.admin</code> |
| <code>/vips add <player> <vip> [--duration time]</code> |Grants VIP directly (optional custom duration) |<code>jvips.admin</code> |
| <code>/vips remove <player> <vip></code> |Removes VIP immediately                        |<code>jvips.admin</code> |
| <code>/vips list [--player name] [--page N]</code> |Shows active VIPs                              |<code>jvips.admin</code> |
| <code>/vips history [--player name] [--page N]</code> |Shows full VIP history                         |<code>jvips.admin</code> |
| <code>/vips reload</code>                      |Reloads configuration and messages             |<code>jvips.admin</code> |

### Duration Format

The `--duration` flag accepts a compact time string:

| Example   |Result                        |
| --------- |----------------------------- |
| <code>30d</code> |30 days                       |
| <code>1d2h10m</code> |1 day, 2 hours, 10 minutes    |
| <code>2h30m</code> |2 hours, 30 minutes           |
| <code>10m</code> |10 minutes                    |
| <code>1d2h10m5s</code> |1 day, 2 hours, 10 min, 5 sec |

***

## 🔐 Permissions

| Permission          |Description                                                     |
| ------------------- |--------------------------------------------------------------- |
| <code>jvips.use</code> |Allows using vouchers and opening menu/status                   |
| <code>jvips.admin</code> |Allows all admin commands                                       |
| <code>jvips.chest.&lt;slots&gt;</code> |Grants virtual chest access (9, 18, 27, 36, 45, or 54)          |

***

## 📁 Configuration

### `config.json`

```json
{
  "language": "en_US",
  "vipExpiry": {
    "sweepEverySeconds": 10
  },
  "vipStack": {
    "enabled": true,
    "maxStack": 3
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
        "lp user {player} parent add thorium"
      ],
      "commandsOnExpire": [
        "lp user {player} parent remove thorium"
      ]
    }
  }
}
```

### `command_vouchers.json`

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
      { "command": "tp {player} hunterquest", "player_side": false },
      { "command": "tellraw {player} [JVIPS] You started the Hunter Quest!", "player_side": true }
    ]
  }
}
```

***

## 🧩 Dependencies

*   **HyUI is required** for `/vips menu` and all menu TAB features.
*   Use a compatible HyUI server build (recommended: `HyUI-0.8.8-release-server-version.jar`).
*   Keep HyUI as a separate server mod (JVIPS uses it as dependency, not bundled runtime library).

## 📥 Installation

1.  Put `JVIPS.jar` in your server mods folder.
2.  Put the compatible `HyUI` jar in your server mods folder.
3.  Start/restart server.
4.  Configure `config.json`, `vips.json`, `command_vouchers.json`, and language files.
5.  Configure permissions (`jvips.use`, `jvips.admin`, `jvips.chest.*`).

## ⚠️ Known Behavior

*   Opening `/vips menu` can still present a short delay on some servers/environments.
*   Current builds already include cache/preload optimizations, but there is no full elimination yet.
*   This is a known point under continuous tuning for future updates.

***

## 📦 Data Storage

| File                |Description                                             |
| ------------------- |------------------------------------------------------- |
| <code>data/players.json</code> |Player VIP states and stack counts                      |
| <code>data/history.json</code> |Full VIP event log (activations, expirations, removals) |
| <code>data/vouchers.json</code> |VIP voucher issuance and usage records                   |
| <code>data/tab_vouchers.json</code> |VIP vouchers delivered directly to menu TAB              |
| <code>data/vipschest.json</code> |Virtual chest contents                                   |

All files use atomic writes (`.tmp` + move) to prevent corruption on crash.

***

## 🔒 Security & Anti-Abuse

### ✅ Implemented

*   HMAC signature validation on VIP vouchers
*   UUID-bound vouchers (VIP and Command)
*   Server-side validation only
*   Sequential command execution
*   Voucher drop-blocking system
*   Atomic file writes for persistent data
*   Custom duration included in HMAC signature

### 🔜 Planned

*   Cooldown per player for activation attempts
*   Max active vouchers per player
*   Admin audit log
*   Webhook support

***

## 📜 License

MIT License
