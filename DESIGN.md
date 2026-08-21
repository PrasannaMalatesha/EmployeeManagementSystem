---
name: Dossier
description: Neo-brutalist visual system for an employee management app — manila paper, oxblood ink stamp, one gloss.
colors:
  manila: "#EFE4C4"
  manila-shade: "#E4D6AC"
  manila-tint: "#F6ECCF"
  ink: "#1A140E"
  oxblood: "#9C1F1F"
  forest: "#2E5D3A"
  ochre: "#C68A00"
  indigo: "#2C3A85"
  ash: "#7A7364"
  on-brand: "#FCF7E5"
  manila-dark: "#1B1610"
  manila-shade-dark: "#2B241C"
  manila-tint-dark: "#24201A"
  ink-dark: "#F0E6CE"
  oxblood-dark: "#EA6060"
  forest-dark: "#7FBB8B"
  ochre-dark: "#F2CE60"
  indigo-dark: "#93A2E6"
  ash-dark: "#A79E88"
  on-brand-dark: "#1B1610"
typography:
  display:
    fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace"
    fontSize: "clamp(2.75rem, 8vw, 5rem)"
    fontWeight: 700
    lineHeight: 0.95
    letterSpacing: "-0.02em"
  heading:
    fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, monospace"
    fontSize: "clamp(1.5rem, 3vw, 2rem)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.01em"
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "normal"
  small:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  micro:
    fontFamily: "ui-monospace, 'SF Mono', Menlo, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "0.1em"
rounded:
  none: "0"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  2xl: "56px"
  3xl: "96px"
components:
  button-primary:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "12px 20px"
  button-primary-hover:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    rounded: "{rounded.none}"
    padding: "12px 20px"
  button-primary-active:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    rounded: "{rounded.none}"
    padding: "12px 20px"
  button-secondary:
    backgroundColor: "{colors.manila-tint}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "12px 20px"
  button-destructive:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "12px 20px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "12px 4px"
  input:
    backgroundColor: "{colors.manila-tint}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "12px 14px"
  card:
    backgroundColor: "{colors.manila-tint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "20px"
  badge:
    backgroundColor: "{colors.manila-tint}"
    textColor: "{colors.ink}"
    typography: "{typography.micro}"
    rounded: "{rounded.none}"
    padding: "4px 10px"
  badge-pending:
    backgroundColor: "{colors.ochre}"
    textColor: "{colors.ink}"
    typography: "{typography.micro}"
    rounded: "{rounded.none}"
    padding: "4px 10px"
  badge-approved:
    backgroundColor: "{colors.forest}"
    textColor: "{colors.on-brand}"
    typography: "{typography.micro}"
    rounded: "{rounded.none}"
    padding: "4px 10px"
  badge-rejected:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    typography: "{typography.micro}"
    rounded: "{rounded.none}"
    padding: "4px 10px"
  avatar:
    backgroundColor: "{colors.oxblood}"
    textColor: "{colors.on-brand}"
    typography: "{typography.micro}"
    rounded: "{rounded.none}"
    size: "56px"
---

## Overview

Dossier is a neo-brutalist visual language modeled on the personnel-file world an HR system inhabits: manila paper, ink typography, an oxblood stamp for authority. It escapes the AI-cliché design cluster (no warm cream + serif + terracotta, no Space Grotesk, no purple gradients, no rounded-lg) by picking a specific subject and honoring it — the paper stock is manila (yellower and warmer than sterile cream), the ink carries a warm undertone, and the accents belong to an office of records, not a fintech landing page. One aesthetic risk (the manila-folder tab in the masthead), spent once, held everywhere else in careful proportion.

The system exists to serve an app whose primary user cares about one action: approve leave. The design's job is to get out of the way for that action while making every other surface — directory, org chart, dashboard, audit trail — read as one drawn thing rather than a bag of components.

Live tile: <https://claude.ai/code/artifact/8d49f7fb-6edf-4d6a-8ba7-9f06c37e2560>.

## Colors

**Palette theory — split-complementary around oxblood.** Oxblood is the brand hue. Its split-complements, forest and indigo, carry approved and informational states. Ochre is the warm analogous partner to oxblood, close enough to sit beside it without collision, reserved for pending. Ash is a hue-biased neutral for secondary text — never pure grey.

**Semantic assignment is one-to-one.** Every semantic color has exactly one meaning; pending is *always* ochre, never oxblood. Approved is *always* forest. Rejected shares oxblood with the brand — a deliberate coupling that reads as "authority acted," and is only ever seen as a rejection badge, never elsewhere.

**Both themes are designed, not inverted.** Light theme grounds on manila with ink text; dark theme grounds on deep ink (`#1B1610`) with warm off-white text (`#F0E6CE`). Every accent hue is lifted for legibility on the dark ground rather than reused verbatim.

**On-brand text.** `on-brand` (`#FCF7E5` light, `#1B1610` dark) is the text/icon color placed on brand surfaces (oxblood buttons, brand-color badges, brand-color avatars). Never place brand text on brand ground.

## Typography

Two typefaces, both from the system font stack — no webfont download, no CDN dependency, no fallback risk.

**Display + headings + micro/eyebrow labels + badges + hex codes + timestamps** all run in **monospace** (`ui-monospace, "SF Mono", Menlo, Consolas, monospace`) at 700 weight with tight tracking. The monospace does the whole voice of the system; the character it carries is what makes the aesthetic feel authored rather than defaulted.

**Body copy** runs in the platform's **native sans** (`ui-sans-serif, system-ui, -apple-system`) at 400/500/700. Pure readability. Body text stays near 62ch max-width.

**Scale** (matches the frontmatter tokens):

| Role | Family | Size | Weight | Leading |
|---|---|---|---|---|
| Display | Mono | clamp(2.75rem, 8vw, 5rem) | 700 | 0.95 |
| H2 | Mono | clamp(1.5rem, 3vw, 2rem) | 700 | 1.05 |
| H3 | Sans | 1.25rem (20px) | 700 | 1.25 |
| Body | Sans | 1rem (16px) | 400 | 1.55 |
| Small | Sans | 0.875rem (14px) | 400 | 1.5 |
| Micro | Mono | 0.75rem (12px), UPPER, tracked 0.1em | 700 | 1.25 |

Tabular numerals (`font-variant-numeric: tabular-nums`) on any digit that appears in a column — leave dates, hours worked, counts. Headings get `text-wrap: balance`.

## Layout

**Container**: single column, `max-width: 1100px`, centered. Side padding `24px` on mobile, `32px` on desktop.

**Section rhythm**: sections stack vertically with `padding: 56px 0` and a `2.5px solid ink` top rule between them — the same weight as component borders, so the page reads as one drawn document rather than boxes floating apart. First section has no top rule.

**Grids that need to be responsive** (palette swatches, card decks, input rows, org children) use `grid-template-columns: repeat(auto-fill, minmax(<min>, 1fr))` with `gap: <token>`. Never per-element margins that silently collapse or double.

**Wide content** (tables, code, org charts) sits inside `overflow-x: auto` on its own container. The page body never scrolls sideways.

**Mobile**: at ≤ 640px, buttons and card grids reflow to single-column, avatar sizes shrink to `44px`, side padding drops to `16px`.

## Elevation & Depth

**Elevation is drawn, not blurred.** The system uses solid offset shadows (`box-shadow: <x> <y> 0 var(--ink)`) with no blur radius. Three canonical steps:

| Level | Shadow | Used on |
|---|---|---|
| Ground | none | Page background, section rules |
| Raised | `4px 4px 0 ink` | Inputs |
| Card | `5px 5px 0 ink` | Buttons |
| Object | `6px 6px 0 ink` | Cards, badges (large), palette swatches, toasts, org nodes, tables |

**Motion under interaction** re-uses the shadow tokens rather than introducing a new one:

- Hover (buttons): `transform: translate(-2px, -2px); box-shadow: 7px 7px 0 ink` — the surface lifts, the shadow grows.
- Active (buttons): `transform: translate(3px, 3px); box-shadow: 2px 2px 0 ink` — the surface presses down into its shadow.
- Focus (inputs): `transform: translate(-1px, -1px); box-shadow: 5px 5px 0 oxblood` — the shadow color shifts to brand.

All motion respects `prefers-reduced-motion: reduce` (transitions and transforms disabled).

**Gloss**, sparingly, on primary CTAs only: a subtle top-edge highlight via `background-image: linear-gradient(180deg, rgba(255,255,255,0.20) 0%, rgba(255,255,255,0.08) 22%, transparent 55%, rgba(0,0,0,0.08) 100%)`. This is the *only* place in the entire system where a gradient appears. Every other surface stays matte.

## Shapes

**Border radius is zero.** No exceptions. The manila-tab in the masthead uses `clip-path: polygon(...)` to cut its angled right edge — that's the only non-rectangular shape in the system.

**Borders are always `2.5px solid ink`.** Thinner reads as flat/unconsidered; thicker fights the type. Badges use `2px` because the surface is small. No dashed, dotted, or gradient borders.

**Avatars are square**, `56px` primary or `44px` compact. Initials are monospace 700 on brand-colored ground.

## Components

Full component vocabulary is expressed in the frontmatter tokens; the sections below describe behavior the token schema cannot carry.

### Buttons

**Variants**: `primary` · `secondary` · `destructive` · `ghost` · `disabled`.

- `primary` is the only button carrying gloss (see Elevation & Depth). Only one primary per screen — if two actions compete for it, one becomes secondary.
- `destructive` uses oxblood without gloss, so it reads as "authority acts" rather than "delightful action."
- `ghost` has no border, no shadow, no fill — a thick 3px underline in ink, hover shifts the underline color to oxblood. Used for cancel and inline dismisses.
- `disabled` retains its shadow but drops to `opacity: 0.42` and disables the hover/active transforms.

Focus ring is `outline: 3px solid indigo; outline-offset: 3px` on all buttons (primary uses `outline-color: on-brand` for contrast on oxblood).

### Inputs

Text inputs, email, number, and password all share the shape. Focus lifts the field one pixel and shifts the shadow to oxblood (see Elevation). Error state uses the same oxblood shadow with `help-error` text set in `oxblood`, weight 700. No icons for error — signal is not color-only when the shadow itself is the primary indicator; the shadow *and* the bolder text together carry the signal.

Placeholder text uses `ash` (muted). Labels sit above the field in `label` style (0.85rem, weight 700).

### Cards

The workhorse surface — an employee, a leave request, a timesheet. All share `manila-tint` bg, `2.5px ink` border, `6×6 ink` shadow, `20px` padding. Content composition varies; the frame is fixed.

Card variants observed in the tile:

- **Employee card** (`.emp-card`): 3-column grid — avatar · name+meta · badge.
- **Leave request card**: card-head (title + badge), meta lines, action row (primary button + ghost).
- **Timesheet card**: card-head (title + badge), meta line only.

### Badges

Small pills (`2px ink` border, `4px 10px` padding, monospace micro type). Semantic variants: `pending` (ochre), `approved` (forest), `rejected` (oxblood), `info` (indigo), `neutral` (manila-tint).

### Toasts / banners

3-column grid: 8px semantic stripe · body (title + small meta) · action row. `6×6 ink` shadow. Semantic stripe color drives the message tone — info (indigo), success (forest), warning (ochre), destructive (oxblood).

### Tables

Rows sit inside the same manila stock as the rest of the app (no floating table surface). Header row uses monospace micro type with `manila-shade` background and a `2.5px ink` bottom rule. Rows separated by `1.5px manila-shade` (subtle divider, not full ink weight — the outer border already frames the table). Minimum row width `720px`; scroll horizontally on narrower viewports.

### Org chart node

Each node is a card variant with a `44×44` colored avatar cell. Connectors between nodes are drawn as SVG lines at the *same 3px stroke weight* as component borders, using `currentColor` so they follow the ink token across themes. The chart reads as one drawn thing.

## Do's and Don'ts

**Do**

- Use monospace for anything with personality — display, headings, badges, hex codes, timestamps, eyebrow labels.
- Reserve the gloss gradient for primary CTAs only. Every other surface stays matte.
- Reach for solid offset shadows (`Npx Npx 0 ink`) — never blurred shadows.
- Use semantic color one-to-one: pending is always ochre, approved is always forest, rejected is always oxblood.
- Give every remote/side-effect action an in-app confirmation (toast) with the semantic stripe on the left rail.
- Use `text-wrap: balance` on headings and `font-variant-numeric: tabular-nums` on any column of digits.
- Design both light and dark themes explicitly — the accent hues lift for legibility on ink ground, they don't just invert.

**Don't**

- Don't use `border-radius` > 0 anywhere. No rounded-lg, no pill buttons, no soft cards.
- Don't use `box-shadow` with a blur radius. Solid offset only.
- Don't reach for warm cream (`#F4F1EA`), Space Grotesk, Inter as display, terracotta accent, or purple-to-blue gradients — the AI-cliché cluster is off-limits.
- Don't use emoji as section markers. `§ Palette` and similar mono eyebrows do that job.
- Don't put gloss on secondary, destructive, ghost, or disabled buttons — gloss is only for primary.
- Don't invent new semantic colors. If a new state appears, extend the meaning of an existing token rather than adding an 8th hue.
- Don't collapse to shadcn defaults (white ground, thin flat borders, soft blurred shadows, blue accent). Overriding the base is a requirement, not a stretch goal.
- Don't fall back to a chart alone when data is presented — always ship a table-view equivalent for accessibility.
