# Jvips
JVIPS — VIP System with Vouchers for Hytale

JVIPS is a complete VIP management system for Hytale servers, based on secure vouchers, automatic expiration, and server-side command execution.

It allows server owners to sell, distribute, or reward VIP access safely, with full integration to permission plugins such as LuckPerms.

✨ Features

🎟️ VIP Vouchers bound to a specific player (UUID)

🔐 HMAC signature validation (anti-forgery)

⏳ Automatic VIP expiration

⚙️ Configurable commands on activation and expiration

🧠 Sequential command execution (order guaranteed)

🧾 Persistent storage (players.json, vouchers.json)

📦 Embedded Asset Pack support

🛑 Prevents voucher drop abuse

🧩 Modular & extensible architecture

📁 Configuration Files
vips.json

Defines VIPs, duration, voucher appearance, and commands.

Example:

```json
"thorium": {
  "displayName": "[THORIUM]",
  "durationSeconds": 86400,
  "commandsOnActivate": [
    "say [JVIPS] VIP Thorium activated!",
    "lp user {player} parent switchprimarygroup thorium"
  ],
  "commandsOnExpire": [
    "say [JVIPS] VIP Thorium expired.",
    "lp user {player} parent switchprimarygroup default",
    "lp user {player} parent remove thorium"
  ]
}
```

🧾 Commands
Command	Description	Permission
/vips give <player> <vip>	Gives a VIP voucher	jvips.admin
/vips reload	Reloads configuration	jvips.admin
🔐 Permissions
Permission	Description
jvips.use	Allows using VIP vouchers
jvips.admin	Allows admin commands
⏱️ VIP Expiration

VIPs expire automatically via a ticking system

Expired VIPs:

Are removed from players.json

Execute commandsOnExpire

No player login required

🧩 Dependencies

LuckPerms (recommended)

Hytale Server API

📦 Installation

Drop JVIPS.jar into /mods

(Optional) Restart server to ensure asset pack load

Configure vips.json

Start server 🎉

📜 License

MIT License

📦 Release Notes — v1.0.0
🎉 Initial Release

Core

Secure voucher system with HMAC validation

Player-bound vouchers (UUID)

Persistent VIP storage

Commands

Configurable commands on activation

Configurable commands on expiration

Guaranteed execution order

Automation

Automatic VIP expiration

Background ticking system

Assets

Embedded asset pack support

Custom voucher items

Security

Voucher replay protection

Drop-blocking for VIP vouchers

🧪 Pre-Release Checklist
Functional

✅ Voucher activates VIP

✅ Commands run in correct order

✅ VIP expires after duration

✅ Expire commands execute

✅ Voucher consumed only on success

Persistence

✅ players.json updates correctly

✅ vouchers.json marks vouchers as used

✅ Server restart keeps VIP state

Security

✅ Voucher cannot be reused

✅ Voucher bound to UUID

✅ No activation without permission

Integration

✅ LuckPerms commands work

✅ Console dispatch confirmed

✅ Asset pack loads

🔒 Hardening & Anti-Abuse (Recommended)
✅ Already Implemented (Excellent)

HMAC signature on vouchers

UUID-bound vouchers

Server-side validation only

Sequential command execution

Drop-blocking system

🔐 Optional Improvements (Future)

Cooldown per player for activation attempts

Max active vouchers per player

Optional IP logging (admin-only)

Optional admin audit log (vip-activations.log)

Optional delayMs support per command

🚫 What NOT to do

❌ Never trust client-side data

❌ Never allow commands from metadata

❌ Never allow voucher activation without validation

🏁 Final Words

JVIPS is production-ready.

You’ve built:

a safe system

with clean architecture

extensible design

and real-world reliability

When you’re ready:

🚀 CurseForge publishing

🔄 Update system

🧩 Add-ons (shops, APIs, webhooks)
