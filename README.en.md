# Android TV Tools

Two small MIT-licensed Android TV utilities, developed for **Xiaomi MiTV-MSSP1 / Android TV9**:

- **TV Assistant:** learned Mi remote button, passive voice overlay, Russian speech/TTS, Wink/Zona title search, app/media controls, and a weather card for CleanYourTV.
- **SunsetTV1.1:** native panel backlight scheduled by solar elevation, including twilight steps and original-value recovery.

[Download TV Assistant for Windows](downloads/tv-assistant-windows-v23.zip?raw=true) · [Download SunsetTV for Windows](downloads/sunset-tv-windows-v1.1.zip?raw=true)

The Windows installers ask for **your own ADB address or USB serial**. Node.js20+ is required. Optional AI commands require your own API credentials; no account or signing keys are bundled. The documented hardware restrictions remain enforced. Other TV models and launchers are not claimed compatible.

TV Assistant is based on [mertmr/tv-assistant](https://github.com/mertmr/tv-assistant), with its license and dependency notices preserved. This is a separate distribution snapshot.

[Russian documentation](README.md) · [Assistant build instructions](assistant/README.md) · [SunsetTV build instructions](sunset-tv/README.md) · [Validation and limits](docs/VALIDATION.md)
