JVIPS – Voucher-based VIP System for Hytale

JVIPS is a flexible and secure VIP management system for Hytale servers, based on vouchers, cryptographic signatures, and automatic expiration handling.

It allows server administrators to grant VIP access using in-game items (vouchers), execute commands on activation/expiration, and manage VIPs manually through admin commands — all without restarting the server.

✨ Features

🎟️ Voucher-based VIP activation

🔐 Secure vouchers (HMAC-signed, player-bound)

⏱️ Automatic VIP expiration (tick-based)

⚙️ Commands on activate / expire

🛠️ Admin commands to add/remove VIPs

🔄 Live config reload (no restart required)

📦 Embedded asset pack (items + interactions)

🧾 JSON-based configuration

🧠 Last known player name tracking (safe even if name changes)

📦 How It Works

An admin gives a VIP voucher to a player.

The voucher item is bound to the player UUID and cryptographically signed.

The player right-clicks the voucher to activate the VIP.

JVIPS:

Validates the voucher

Applies the VIP

Executes configured commands

Starts tracking expiration

When the VIP expires:

The VIP is removed automatically

Expiration commands are executed

🧑‍💼 Commands
Player / Staff Commands
/vips givekey <vipId> <player>

Gives a VIP voucher to a player.

Requires the player to be online

The voucher is bound to the target player

Permission:
jvips.admin

Admin Commands
/vips add <player> <vipId>

Adds a VIP directly to a player without using a voucher.

Respects the rule: only one active VIP per player

Uses the VIP duration defined in vips.json

Triggers commandsOnActivate

Permission:
jvips.admin

/vips remove <player> <vipId>

Removes an active VIP from a player.

Only removes if the specified VIP matches the active one

Triggers commandsOnExpire

Permission:
jvips.admin

/vips reload

Reloads all JVIPS configuration files without restarting the server.

Reloaded files:

vips.json

players.json

vouchers.json

Permission:
jvips.admin

🔑 Permissions
Permission	Description
jvips.use	Allows players to activate VIP vouchers
jvips.admin	Full administrative access to JVIPS
⚙️ Configuration (vips.json)

Each VIP is fully configurable.

Example VIP configuration

```json
"thorium": {
  "displayName": "[THORIUM]",
  "durationSeconds": 86400,
  "voucher": {
    "itemId": "Jvips_Voucher",
    "name": "[THORIUM] Voucher #{voucherIdShort}",
    "lore": [
      "Activates: [THORIUM]",
      "Duration: {durationHuman}",
      "Bound to: {player}",
      "Right-click to activate"
    ]
  },
  "commandsOnActivate": [
    "say [JVIPS] You activated the Thorium VIP.",
    "lp user {player} parent add thorium",
    "lp user {player} parent switchprimarygroup thorium"
  ],
  "commandsOnExpire": [
    "say [JVIPS] Your Thorium VIP has expired.",
    "lp user {player} parent switchprimarygroup default",
    "lp user {player} parent remove thorium"
  ]
}
```
Available placeholders
Placeholder	Description
{player}	Player name (or UUID fallback)
{vipId}	VIP identifier
{durationHuman}	Human-readable duration
{voucherIdShort}	Short voucher ID
⏱️ Automatic VIP Expiration

JVIPS includes an internal tick-based system that:

Checks for expired VIPs every few seconds

Automatically removes expired VIPs

Executes commandsOnExpire in the correct order

No cron jobs, no schedulers, no external dependencies.

🔐 Security & Anti-Abuse

Vouchers are HMAC-signed

Vouchers are bound to a specific player UUID

Used vouchers are tracked and cannot be reused

Admin actions bypass vouchers but still respect VIP rules

📁 Data Storage

JVIPS stores data in its plugin directory:

vips.json – VIP definitions and commands

players.json – Active VIP states (UUID-based)

vouchers.json – Issued and used vouchers

Player UUIDs are used internally.
Player name changes are handled safely via lastKnownName.

📦 Asset Pack

JVIPS embeds its asset pack directly inside the plugin JAR.

No manual installation required

Items and interactions are automatically available

Compatible with standard Hytale asset loading

🧪 Compatibility

Designed for Hytale server builds with:

CommandManager

Interaction system

ECS ticking systems

Tested with LuckPerms for permission/group handling

❤️ Credits

Developed by Julio (JVIPS)
With a strong focus on:

Clean architecture

Server safety

Administrative flexibility

If you want, next steps I can help you with:

📦 Release notes (first public release)

🧪 Final pre-publish checklist

🔒 Advanced hardening ideas

🌍 Localization support

📈 Future roadmap (VIP stacking, extensions, UI, etc.)
