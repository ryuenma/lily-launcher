# Lily launcher — screens

Live on-device captures from the POCOPHONE F1 (1080×2246). Lily is 1024×600 landscape and
scales to any window, so on a portrait phone it is pillarboxed. On the real K2501 head unit
(1024×600 native) it fills the panel exactly — see `docs/screenshot-day.png` and
`docs/screenshot-night.png` for the head-unit renders that ship in the repo.

| File | Capture | State |
|---|---|---|
| `phone-pillarboxed-empty.png` | `screencap` via adb, after `KEYCODE_WAKEUP` + `wm dismiss-keyguard` | Lily rendering (not black). EMPTY: no BT, no track, vol 0. The phone lacks the NWD vendor services (`com.bt.bc03`, `com.nwd.radio.service`, `com.zjinnova.zlink`), so `Car` degrades gracefully — binders stay null, `mcu_*` reads return defaults, UI shows "No signal / Press SWITCH to connect a phone". |

The app is intentionally inert without the vendor layer, which is why this is the only state
we can capture off-headunit. Full BT / Radio / USB / AA behaviours require the real K2501
(head unit (when online) when online) or a mock provider — see the mockup in `mockup/index.html`.

## Head-unit mockup renders (ship in repo)

| File | Theme |
|---|---|
| `../screenshot-day.png` | Day / graphite (light) |
| `../screenshot-night.png` | Night (dark) |
| `../before-stock.jpg` | Stock NWD launcher |

## What renders (all states)

- **Tabs**: device name (orange when BT) · USB · AA + Volume
- **Clock plate**: h:mm AM/PM | weekday over date (clock receiver)
- **Display (`lcd`)**:
  - TAPE: tag + device + title (wrap 2→3, shrink 1.0→0.82→0.7) + artist (accent) + readout Time/Length
  - RADIO: band + station + freq (FM 87.5–108 MHz scale with accent needle) + readout MHz/kHz
  - EMPTY: "No signal" + "Press SWITCH to connect a phone" + "WAITING", pads dimmed 45%
- **Pads**: PREV/PLAY/NEXT (seek in radio) · BT/RADIO/USB latched + LEDs · PHONE · SWITCH · AUTO · MORE
- **Knob**: 21 ticks to vol, drag 6px/step, tap opens EQ, "0 TAP FOR EQ"
- **Deck** (TAPE only): 2 reels 3.2s/2.1s, 8 bars, glow 1.6s — only while playing, 30fps
- **Volume flash**: "Vol 88 → 0X of 40" 1.5s

**More sheet** (13 tiles): Radio · USB Music · Video · Dashcam · Sound EQ · AUX · Files · Car · CAN · Wheel keys · Android (Settings) · Screen off · Home setup → Settings. Last two are special (screenoff launches `com.android.launcher/com.nwd.screenoff.ScreenOffActivity`; settings opens the Settings sheet). Others launch vendor components via `Car.launch()`.

**Settings sheet** (LED per option):
- `resumeOnStart` true/false — auto-play on BT connect
- `autoAA` true/false — auto-open zlink on AA connect
- `theme` auto/dark — auto follows headlights (night), dark forces graphite
- `accent` #FF5A1F / #258AC3 / #2FB35A / #F5B400 / #E8332A

## Mockup simulator captures (all states)

Rendered from `mockup/index.html` via its simulator panel (Edge headless, 1052×628). These show every UI state the app can be in; the on-device captures above only cover the empty state.

| File | State |
|---|---|
| `mockup-tape-day.png` | TAPE: BT music playing, day theme (silver) |
| `mockup-tape-night.png` | TAPE: BT music playing, night theme (graphite, headlights on) |
| `mockup-radio.png` | RADIO: FM station, seek needle, frequency readout |
| `mockup-usb.png` | USB: USB music playing, title/artist |
| `mockup-aa-connected.png` | Android Auto connected (zlink) |
| `mockup-reverse.png` | Reverse gear overlay (camera on top) |
| `mockup-incoming-call.png` | Incoming call overlay |
| `mockup-more-sheet.png` | "More" sheet (13 app tiles) |
| `mockup-settings-sheet.png` | Settings sheet (resumeOnStart, autoAA, theme, accent) |
| `mockup-empty.png` | Empty: no BT, no track, pads dimmed 45% |
