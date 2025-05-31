<div align="center">
  
# Lyttle Essentials

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)](https://papermc.io)
[![Hangar](https://img.shields.io/badge/Hangar-download-success)](https://hangar.papermc.io/Lyttle-Development)
[![Discord](https://img.shields.io/discord/941334383216967690?color=7289DA&label=Discord&logo=discord&logoColor=ffffff)](https://discord.gg/QfqFFPFFQZ)

> ✨ **A lightweight yet powerful essentials plugin with integrated economy and smart moderation features!** ✨

[📚 Features](#--features) • [⌨️ Commands](#-%EF%B8%8F-commands) • [🔑 Permissions](#--permissions) • [📥 Installation](#--installation) • [⚙️ Configuration](#%EF%B8%8F-configuration) • [📱 Support](#--support)

</div>

![Divider](https://raw.githubusercontent.com/Lyttle-Development/LyttleUtils/refs/heads/main/line.png)

## 🌟 Features

### 🎯 Core Plugin Features
- Token-based Economy System with configurable pricing
- Essential teleportation commands (/home, /warp, /spawn, /tpa)
- Smart chat filtering with regex-based moderation
- Progressive pricing system for certain commands
- Customizable message system

---

### 🤌 Lyttle Certified
- Clean and efficient codebase
- No unnecessary features
- Full flexibility and configurability
- Open source and free to use (MIT License)

---

## ⌨️ Commands

> 💡 `<required>` `[optional]`

| Command               | Permission           | Description                     |
|:---------------------|:--------------------|:--------------------------------|
| `/home`              | `essentials.home`   | Teleport to your home          |
| `/setwarp <name>`    | `essentials.setwarp`| Create a new warp location     |
| `/warp <name>`       | `essentials.warp`   | Teleport to a warp location    |
| `/tpa <player>`      | `essentials.tpa`    | Request to teleport to a player|
| `/spawn`             | `essentials.spawn`  | Teleport to spawn location     |

<details>
<summary>📌 Click to see more commands</summary>

| Command                    | Permission                | Description                     |
|:--------------------------|:-------------------------|:--------------------------------|
| `/essentials reload`      | `essentials.admin.reload`| Reload the plugin configuration |
| `/tokens <check/transfer>`| `essentials.tokens`     | Manage your token balance      |

</details>

---

## 🔑 Permissions

| Permission Node              | Description                          | Default |
|:----------------------------|:-------------------------------------|:--------|
| `essentials.*`              | Grants all plugin permissions        | `❌`     |
| `essentials.user.*`         | Grants all user permissions          | `✔️`    |
| `essentials.admin.*`        | Grants all admin permissions         | `❌`     |
| `essentials.teleport.*`     | Access to all teleport commands      | `❌`     |
| `essentials.economy.*`      | Access to all economy features       | `❌`     |

---

## 📥 Installation

### Quick Start
1. Download the latest version from Hangar
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Edit the configuration file to customize prices and features
5. Use `/essentials reload` to apply changes

---

### 📋 Requirements
- Java 21 or newer
- Paper 1.21.x+
- Minimum 20MB free disk space

---

### 💫 Soft Dependencies (Optional)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) - For economy integration

---

### 📝 Configuration Files
#### 🔧 `config.yml`
Controls economy settings and service pricing:
- Configurable prices for teleportation commands
- Progressive pricing system
- Economy integration settings

#### 💬 `data/chat_filter.txt`
Contains regex patterns for chat moderation:
- Advanced pattern matching for inappropriate content
- Configurable filter rules
- Easy to update and maintain

### 🔄 The #defaults Folder
The folder serves several important purposes: `#defaults`
1. **Backup Reference**: Contains original copies of all configuration files
2. **Reset Option**: Use these to restore default settings
3. **Update Safety**: Preserved during plugin updates
4. **Documentation**: Shows all available options with comments

> 💡 **Never modify files in the #defaults folder!** They are automatically overwritten during server restarts.
---

## 💬 Support

<div align="center">

### 🤝 Need Help?

[![Discord](https://img.shields.io/discord/941334383216967690?color=7289DA&label=Join%20Our%20Discord&logo=discord&logoColor=ffffff&style=for-the-badge)](https://discord.gg/QfqFFPFFQZ)

🐛 Found a bug? [Open an Issue](https://github.com/LyttleDevelopment/LyttleEssentials/issues)  
💡 Have a suggestion? [Share your idea](https://github.com/LyttleDevelopment/LyttleEssentials/issues)

</div>

---

## 📜 License

<div align="center">

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

### 🌟 Made with the lyttlest details in mind by [Lyttle Development](https://www.lyttledevelopment.com)

If you enjoy this plugin, please consider:

⭐ Giving it a star on GitHub <br>
💬 Sharing it with other server owners<br>
🎁 Supporting development through [Donations](https://github.com/LyttleDevelopment)

![Divider](https://raw.githubusercontent.com/Lyttle-Development/LyttleUtils/refs/heads/main/line.png)

</div>
