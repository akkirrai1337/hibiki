<div align="center">

  <img src="./docs/hibiki-round.svg" alt="hibiki icon" width="120" height="120"/>

  # hibiki

  [Русский](README_RU.md)

  **hibiki is an anime client for browsing titles, managing a personal library, and watching episodes with saved progress. Sources are connected through external repositories; the app does not host or distribute content.**

  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

</div>

### 📚 Features

* Browse anime from your chosen sources, with search, filters, and sorting
* Connect external source repositories and manage extensions ([hibiki-sources](https://github.com/akkirrai1337/hibiki-sources))
* Explore title details, descriptions, genres, related and similar anime
* Choose episodes and voice-overs, and adjust playback quality and speed
* Built-in player with HLS, DASH, and MP4 support, autoplay, and opening/ending skip
* Save watch progress, resume episodes, and view your watch history and statistics
* Organize your local library: watching, planned, completed, dropped, on hold, and favourites
* Download episodes for offline viewing
* Customize the appearance and interface language, and optionally enable Discord Rich Presence

### 📥 Install

Download from the [Releases](https://github.com/akkirrai1337/hibiki/releases) page, or:

* **Windows**: run `hibiki-v…-setup.exe`.
* **Android**: install `hibiki-v….apk`.
* **macOS** and **Linux**, one command:

  ```sh
  curl -fsSL https://raw.githubusercontent.com/akkirrai1337/hibiki/main/scripts/install.sh | sh
  ```

  It picks the build for your system (macOS Apple Silicon or Intel, Linux x64), checks it against the release's checksum and installs it: `hibiki.app` into `/Applications`, or an AppImage as `~/.local/bin/hibiki` with a menu entry. A Linux AppImage needs FUSE 2 (`sudo apt install libfuse2` on Debian/Ubuntu).

  **macOS and Gatekeeper.** The app is not signed with an Apple Developer certificate, so a copy you download in a browser is blocked on first launch. Installing with the command above avoids that. If you used the `.dmg` instead: drag the app to Applications, then run `xattr -cr /Applications/hibiki.app` once (or, on macOS 15, allow it in System Settings → Privacy & Security → "Open Anyway").

Updates are offered inside the app. On Windows it runs the new installer; on macOS and Linux it swaps the app itself and restarts (if the app sits somewhere you cannot write to, it opens the release page instead).

### 🖼️ Screenshots


<div align="center">

<h4>Android</h4>

<table>
  <tr>
    <td><img src="./docs/screenshots/android-home.jpg" alt="Android home screen" width="250"/></td>
    <td><img src="./docs/screenshots/android-details.jpg" alt="Android title details" width="250"/></td>
  </tr>
</table>

<h4>Desktop</h4>

<table>
  <tr>
    <td><img src="./docs/screenshots/desktop-home.png" alt="Desktop home screen" width="400"/></td>
    <td><img src="./docs/screenshots/desktop-details.png" alt="Desktop title details" width="400"/></td>
  </tr>
</table>

</div>

### 🎬 Credits

- [Anikku](https://github.com/komikku-app/anikku) and [Yuzono anime-extensions](https://github.com/yuzono/anime-extensions): the Aniyomi extension API and compatibility code, used under Apache-2.0.

### 💬 Contact

For questions, suggestions, or bug reports, you can contact me on Discord: `akkirrai`

### 📄 License

hibiki is licensed under the [GNU General Public License v3.0](LICENSE).

### ⚖️ DMCA Disclaimer

The developer of this application does not have any affiliation with the content available in the app and does not store or distribute any content. This application should be considered a web browser, and all content that can be found using this application is freely available on the Internet. All DMCA takedown requests should be sent to the owners of the website where the content is hosted.
