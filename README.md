<div align="center">
  
# LyttleNametag

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)](https://papermc.io)
[![Hangar](https://img.shields.io/badge/Hangar-download-success)](https://hangar.papermc.io/Lyttle-Development)
[![Discord](https://img.shields.io/discord/941334383216967690?color=7289DA&label=Discord&logo=discord&logoColor=ffffff)](https://discord.gg/QfqFFPFFQZ)

> ✨ **Better Nametags Plugin - Supports Newlines!** ✨

[📚 Features](#--features) • [⌨️ Commands](#-%EF%B8%8F-commands) • [🔑 Permissions](#--permissions) • [📥 Installation](#--installation) • [⚙️ Configuration](#%EF%B8%8F-configuration) • [📱 Support](#--support)

</div>

![Divider](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/rainbow.png)

## 🌟 Features

<p align="center">
  <img src="https://github.com/Lyttle-Development/LyttleNametag/blob/main/LyttleNametag-Example.gif?raw=true" alt="Feature Showcase" width="500px">
</p>

### 🎯 Core Plugin Features
- **Multi line nametags**: Display stacked, gorgeous lines above players.
- **Self-Viewing**: Allow players to see their own nametag (configurable, requires permission or OP status).
- **Tamed Mobs Support**: Render gorgeous, dynamic double-line floating nametags over tamed pets (wolves, cats, horses). Line 1 displays the owner's prefix/Vault group style; Line 2 displays the pet's custom name (in clean, pristine white text).
- **Colors & formatting support**: Full legacy/minimessage support.
- **PlaceholderAPI integration**: Display dynamic server, player, and system placeholders seamlessly.

---

### 🤌 Lyttle Certified
- Basic plugin without fluff
- No unnecessary features
- Full flexibility and configurability
- Open source and free to use (MIT License)

---

## ⌨️ Commands

> 💡 `<required>` `[optional]`

| Command               | Permission      | Description                  |
|:----------------------|:----------------|:-----------------------------|
| `/plugin reload`      | `plugin.reload` | Reloads the configuration    |

---

## 🔑 Permissions

| Permission Node       | Description                  | Default |
|:----------------------|:-----------------------------|:--------|
| `LyttleNametag.LyttleNametag` | Ability to reload the plugin | `❌`     |
| `lyttlenametag.viewself` | Ability to view own nametag if enabled | `OP`   |

---

## 📥 Installation

### Quick Start
1. Download the latest version from [Hangar](https://hangar.papermc.io/Lyttle-Development/LyttleNametag)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Edit the configuration file to customize the plugin to your needs
5. Use `/LyttleNametag reload` to apply changes

---


### 📋 Requirements
- Java 21 or newer
- Paper 1.21.x+
- Minimum 20MB free disk space

---


### 💫 Dependencies
- [PlaceholderAPI](https://hangar.papermc.io/HelpChat/PlaceholderAPI) (for dynamic content)

---


### 📝 Configuration Files
#### 🔧 `config.yml`
The main configuration file controlling plugin behavior and features. Key settings include:
- `nametag`: The default multi-line template for player nametags.
- `view_distance`: Maximum block distance within which the nametag is spawned/updated.
- `view_self`: (boolean, default `true`) Toggle whether players can see their own nametag. Requires OP or `lyttlenametag.viewself` permission.
- `groups`: Primarily used to override multi-line templates based on the player's primary Vault group (e.g., `admin`).
- `tamed_mobs`: Dynamic double-line floating nametag configuration for tamed pets:
  - `enabled`: (boolean, default `true`) Enable the feature.
  - `show_unnamed`: (boolean, default `false`) Render nametags even if the pet doesn't have a custom name (displays capitalized entity type, e.g. "Wolf").

#### 💬 `messages.yml`
Customize all plugin messages. Supports color codes and placeholders.

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

🐛 Found a bug? [Open an Issue](https://github.com/Lyttle-Development/LyttleNametag/issues)  
💡 Have a suggestion? [Share your idea](https://github.com/Lyttle-Development/LyttleNametag/issues)

</div>

---

## 📜 License

<div align="center">

This project is licensed under the MIT License - see the [LICENSE](https://github.com/Lyttle-Development/LyttleNametag/blob/main/LICENSE) file for details.

---

### 🌟 Made with the lyttlest details in mind by [Lyttle Development](https://www.lyttledevelopment.com)

If you enjoy this plugin, please consider:

⭐ Giving it a star on GitHub <br>
💬 Sharing it with other server owners<br>
🎁 Supporting development through [Donations](https://github.com/LyttleDevelopment)

![Divider](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/rainbow.png)

</div>