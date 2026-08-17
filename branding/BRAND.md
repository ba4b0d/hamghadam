# HamGhadam (همقدم) — Brand & Logo
> "walking in step together"

## Source art
- `hamghadam-logo.svg` — primary mark (drawn 2026-08-17, research). Concept: two shared "footsteps in step" (ahead + rear) over a warm rounded-square, with a motion line — friends moving together.

## Palette
| Token | Hex | Use |
|---|---|---|
| Ember | `#F15B2A` | background gradient end / accent |
| Sun | `#FFBA08` | background gradient start |
| Sand | `#FFD166` / `#FFE9A8` | foreground feet |
| White | `#FFFFFF` | motion line, pace dots, negative space |

## Launcher (Android adaptive icon) — frontend-dev to build
- Background: `#FFBA08 → #F15B2A` gradient (or flat if adaptive bg can't gradient: use `#F15B2A`).
- Foreground: the two-footstep mark, flattened white/`#FFD166`, centered in the 66dp safe zone.
- Provide `mipmap-*` densities + round variant from the SVG.
- Keep `applicationId com.fitnessapp.android` (unchanged).

## In-app usage (v1, minimal)
- Splash / onboarding header: the mark at 96–128dp.
- Empty-state illustration on the Dashboard "Connect your watch" card: reuse mark + tagline.

## Rules
- One mark, two tones: **warm** = launcher/splash; **mono white** = on-busy backgrounds.
- No text in the app icon (launcher label already shows "HamGhadam همقدم").