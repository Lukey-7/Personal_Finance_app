---
name: Boro
colors:
  surface: '#fcf8f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fcf8f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f2'
  surface-container: '#f1edec'
  surface-container-high: '#ebe7e7'
  surface-container-highest: '#e5e2e1'
  on-surface: '#1c1b1b'
  on-surface-variant: '#454558'
  inverse-surface: '#313030'
  inverse-on-surface: '#f3f0ef'
  outline: '#757589'
  outline-variant: '#c5c4db'
  surface-tint: '#343dff'
  primary: '#0001bb'
  on-primary: '#ffffff'
  primary-container: '#0000ff'
  on-primary-container: '#b3b7ff'
  inverse-primary: '#bec2ff'
  secondary: '#5a5f66'
  on-secondary: '#ffffff'
  secondary-container: '#dbe0e8'
  on-secondary-container: '#5e636a'
  tertiary: '#333637'
  on-tertiary: '#ffffff'
  tertiary-container: '#4a4c4e'
  on-tertiary-container: '#bbbcbe'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e0e0ff'
  primary-fixed-dim: '#bec2ff'
  on-primary-fixed: '#00006e'
  on-primary-fixed-variant: '#0000ef'
  secondary-fixed: '#dee3eb'
  secondary-fixed-dim: '#c2c7cf'
  on-secondary-fixed: '#171c22'
  on-secondary-fixed-variant: '#42474e'
  tertiary-fixed: '#e1e2e4'
  tertiary-fixed-dim: '#c5c6c8'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#fcf8f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  display-numeric:
    fontFamily: Inter
    fontSize: 82px
    fontWeight: '400'
    lineHeight: 100%
    letterSpacing: -0.04em
  screen-title:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  body-main:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: '0'
  label-muted:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  button-label:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: -0.01em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  page-margin: 24px
  section-gap: 48px
  row-padding: 16px
  element-gap: 12px
  safe-area-bottom: 34px
---

## Brand & Style

The design system is defined by a "Quiet Confidence" philosophy. Inspired by the architectural minimalism of Polestar, the aesthetic prioritizes essentialism, precision, and an unhurried user experience. It avoids the typical "loud" signals of fintech, opting instead for a monochrome foundation punctuated by a single, high-energy primary blue.

The style is a clinical evolution of Minimalism. It relies on extreme whitespace, massive typographic scale shifts, and high-quality "negative" space to create a premium, gallery-like feel. Interaction is hushed; there are no heavy gradients or complex shadows—only pure forms and precise alignment.

## Colors

The palette is strictly functional. The background uses a warm off-white to reduce retinal strain while maintaining a pristine atmosphere. 

- **Primary Action:** Pure Blue is reserved exclusively for high-intent interactive elements and critical state indicators.
- **Text:** Near-black provides maximum legibility without the harshness of absolute black.
- **Secondary Text:** A neutral gray used for metadata and supportive labels to maintain typographic hierarchy.
- **UI Accents/Pills:** A soft gray used for decorative containment or low-priority background elements.
- **Surface:** Pure white is used sparingly for cards or distinct container layers to provide subtle lift against the off-white background.

## Typography

This design system uses Inter for its technical precision and neutrality. The hierarchy is driven by extreme scale rather than weight alone. 

- **Numerical Hero:** Amounts and balances use oversized 82px type to turn data into a visual focal point.
- **Screen Titles:** Set at 22px to provide a clear anchor point for navigation.
- **Micro-Copy:** Secondary labels are often muted in color and slightly reduced in size, using uppercase tracking for a clean, architectural feel.
- **Vertical Rhythm:** Line heights are generous to prevent the dense "data-heavy" look common in financial apps.

## Layout & Spacing

The layout follows a native iOS portrait model (393x852) with an emphasis on vertical flow. 

- **Open Rows:** List items and data rows are "open"—they do not sit inside boxed containers. They are separated by whitespace or extremely faint 1px dividers.
- **Massive Whitespace:** Vertical gaps between sections (e.g., between a balance and a list) should be significantly larger than standard UI patterns to emphasize a premium feel.
- **Floating Actions:** Primary interactions are housed in floating pill-style components at the bottom of the screen, ensuring they remain accessible while allowing the content to breathe.

## Elevation & Depth

Depth is conveyed through tonal layering rather than shadows. 

- **Tonal Layers:** The interface is flat. Contrast between the off-white background (#FDFCFB) and pure white surfaces (#FFFFFF) creates a subtle "stacked" effect.
- **Low-Contrast Outlines:** Where containment is necessary, use 1px strokes in UI Accents (#F2F3F5). 
- **Shadows:** Avoid drop shadows entirely. If depth is required for a floating button, use a very large, ultra-low opacity (2-4%) neutral blur that is barely perceptible.

## Shapes

The shape language is dominated by the "Pill." 

- **Action Elements:** Buttons and tags use a full corner radius to create a soft, approachable contrast to the sharp, precise typography.
- **Logo:** The uppercase 'B' is enclosed in a 1.5px stroke circle, echoing the roundedness of the UI components.
- **Cards:** While mostly "open," any contained surfaces use a generous 24px (rounded-xl) corner radius to maintain the premium, modern aesthetic.

## Components

- **Floating Pill Button:** The primary interaction. Full-width or centered pill, #0000FF background with #FFFFFF text. Positioned floating 24px from the screen bottom.
- **Open Rows:** Transaction or list items. No background, 16px vertical padding, #050505 for titles, #969BA3 for secondary details.
- **Numerical Inputs:** Center-aligned, 82px Inter, no border or box. A simple blinking cursor in #0000FF indicates focus.
- **Status Chips:** Small pills using #F2F3F5 background and #969BA3 text for neutral states; #0000FF text for active states.
- **The Logo:** Minimalist 32x32px component. A thin 1.5px circle containing a centered, medium-weight uppercase 'B'.