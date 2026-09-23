---
name: Boro UI
colors:
  surface: '#fcf8f8'
  surface-dim: '#ddd9d8'
  surface-bright: '#fcf8f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f7f3f2'
  surface-container: '#f1edec'
  surface-container-high: '#ebe7e7'
  surface-container-highest: '#e5e2e1'
  on-surface: '#1c1b1b'
  on-surface-variant: '#444747'
  inverse-surface: '#313030'
  inverse-on-surface: '#f4f0ef'
  outline: '#747878'
  outline-variant: '#c4c7c7'
  surface-tint: '#5e5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1c1c'
  on-primary-container: '#848483'
  inverse-primary: '#c7c6c6'
  secondary: '#0050d7'
  on-secondary: '#ffffff'
  secondary-container: '#2469ff'
  on-secondary-container: '#fefcff'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#1e1b1b'
  on-tertiary-container: '#888382'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e3e2e1'
  primary-fixed-dim: '#c7c6c6'
  on-primary-fixed: '#1b1c1c'
  on-primary-fixed-variant: '#464747'
  secondary-fixed: '#dbe1ff'
  secondary-fixed-dim: '#b4c5ff'
  on-secondary-fixed: '#00174c'
  on-secondary-fixed-variant: '#003da9'
  tertiary-fixed: '#e8e1e0'
  tertiary-fixed-dim: '#ccc5c4'
  on-tertiary-fixed: '#1e1b1b'
  on-tertiary-fixed-variant: '#4a4646'
  background: '#fcf8f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  display-xl:
    fontFamily: Inter
    fontSize: 82px
    fontWeight: '600'
    lineHeight: '1.0'
    letterSpacing: -0.04em
  display-lg:
    fontFamily: Inter
    fontSize: 60px
    fontWeight: '600'
    lineHeight: '1.1'
    letterSpacing: -0.03em
  heading-md:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '500'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
    letterSpacing: '0'
  numeric-data:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.0'
    letterSpacing: -0.055em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1.0'
    letterSpacing: 0.05em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 4px
  xs: 8px
  sm: 16px
  md: 24px
  lg: 48px
  xl: 80px
  container-margin: 40px
  gutter: 24px
---

## Brand & Style

This design system is built on the principle of **Polestar-style restraint**: a surgical precision where every pixel must earn its place. It targets a sophisticated fintech audience that values clarity over decoration and speed over visual noise. 

The aesthetic is hyper-minimalist, leaning into a "Gallery" approach where financial data is treated like art. By utilizing maximum whitespace and a warm, off-white foundation, the UI evokes an emotional response of calm, control, and premium exclusivity. It avoids the "tech-heavy" clichés of glow and depth, opting instead for a flat, architectural layering of information.

## Colors

The palette is anchored by an organic off-white (`#FDFCF9`), which provides a warmer, more premium feel than pure digital white. Contrast is driven by a deep black (`#070808`) for primary actions and "Ink" typography (`#111214`) for high readability.

Functional colors—Blue, Green, Red, Orange, and Purple—are used sparingly as data indicators rather than decorative elements. The "Soft" and "Line" tones facilitate subtle containment without introducing heavy visual weight or traditional borders.

## Typography

The typographic system utilizes **Inter** for its neutral, systematic clarity. The hierarchy is intentionally dramatic: oversized "Display" headings create a focal point of confidence, while "Numeric Data" styles use tight letter spacing (-0.055em) to give account balances and market figures a technical, precise appearance.

For body copy, ample line height (1.5–1.6) is maintained to ensure the "maximum whitespace" ethos extends to the reading experience. Labels are small and occasionally tracked out to provide a structural contrast to the massive display titles.

## Layout & Spacing

This design system employs a **Fluid Grid** with generous safe areas. Layouts are defined by wide margins (40px+) to push content toward the center, creating a sense of focus. 

The spacing rhythm follows a strict 4px base unit, but shifts toward the larger end of the scale (`lg` and `xl`) to maintain the "restrained" aesthetic. Elements are grouped in "Soft Panels" with internal padding of 32px to 48px, ensuring data never feels cramped.

## Elevation & Depth

Depth is conveyed through **Tonal Layering** rather than shadows. The background (`#FDFCF9`) acts as the base canvas. Secondary information is placed on "Soft" surfaces (`#F2F4F6`). 

There are no heavy shadows; if elevation is required for a floating element (like the navigation pill), a micro-stroke (`#E7E8EA`) or a virtually invisible, extra-diffused 2% black shadow is used. This flat-stacking approach ensures the UI feels light and responsive.

## Shapes

The shape language is characterized by "Hyper-Roundedness." Primary action elements and navigation use **Pill-shaped** geometry. 

- **Buttons:** Fixed at 29px radius to create a distinct, friendly silhouette.
- **Panels:** Large 24px radius for a soft, architectural feel.
- **Inputs:** A more moderate 12px radius to balance functionality with the overall soft-form language.

## Components

### Buttons
Primary buttons are solid Black (`#070808`) with white text, featuring a 29px radius and 56px height. Secondary buttons use the Soft (`#F2F4F6`) fill with Text-Main (`#111214`) type.

### Floating Pill Navigation
The main navigation is a floating element at the bottom of the viewport. It uses a Soft background or a backdrop-blur (90% opacity white) with a 1px Line (`#E7E8EA`) border and pill-shaped radius. Icons are minimal 1.5pt lines.

### Soft Panels
Information clusters are contained within Soft (`#F2F4F6`) panels. These panels do not have borders or shadows; they rely entirely on the color contrast against the background to define their shape.

### Input Fields
Minimalist execution. Inputs are defined by a bottom-border only (`#E7E8EA`) in an inactive state, moving to a full Soft-fill background when focused. No heavy boxing.

### Lists & Data Rows
Rows are separated by thin Line (`#E7E8EA`) dividers. Tapping a row triggers a subtle background shift to Soft, with no "heavy" press state.

### Minimal Icons
Icons must be "Open-stroke" with a consistent 1.5px or 2px weight, using the Muted-Dark (`#646A72`) color for secondary actions.