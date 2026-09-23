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
  secondary-container: '#2569fe'
  on-secondary-container: '#fefcff'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#1e1b1b'
  on-tertiary-container: '#898282'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e4e2e1'
  primary-fixed-dim: '#c7c6c6'
  on-primary-fixed: '#1b1c1c'
  on-primary-fixed-variant: '#464747'
  secondary-fixed: '#dbe1ff'
  secondary-fixed-dim: '#b4c5ff'
  on-secondary-fixed: '#00174c'
  on-secondary-fixed-variant: '#003da9'
  tertiary-fixed: '#e9e0e0'
  tertiary-fixed-dim: '#cdc5c4'
  on-tertiary-fixed: '#1e1b1b'
  on-tertiary-fixed-variant: '#4b4645'
  background: '#fcf8f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  display-balance:
    fontFamily: Inter
    fontSize: 64px
    fontWeight: '600'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  display-input:
    fontFamily: Inter
    fontSize: 80px
    fontWeight: '500'
    lineHeight: '1.0'
    letterSpacing: -0.04em
  section-header:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: -0.01em
  screen-title:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.2'
  body-main:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-caps:
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
  base: 4px
  container-padding: 24px
  element-gap: 16px
  section-margin: 40px
  gutter: 12px
---

## Brand & Style

The design system is defined by a philosophy of high-utility minimalism and "Polestar-inspired" restraint. It targets a sophisticated user base that values clarity over decoration. The emotional response is one of calm confidence and absolute precision.

The visual style is strictly **Minimalist**, leaning into heavy whitespace and a reductionist approach to interface elements. By eliminating heavy shadows and complex gradients, the focus shifts entirely to typography and spatial relationships. The interface feels architectural—structural integrity is maintained through thin separators and a deliberate use of soft-tonal panels rather than depth-based layering.

## Colors

This design system utilizes a "Warm Gallery" palette. The background is not a pure white but a soft off-white (#FCFCFA), providing a premium, paper-like quality that reduces eye strain. 

- **Core Neutrals:** Black (#080909) is used for high-impact primary actions, while Text (#101112) is reserved for standard readability.
- **Tonal Layers:** Soft-panel (#F1F3F5) and Soft-panel-2 (#F6F7F8) are used to group content without adding visual weight.
- **Functional Accents:** Blue (#075CF2) signals interaction and progress. Green, Red, and Orange are used strictly for financial status indicators (gain, loss, warning).
- **Muted Tiers:** Three levels of muted greys (A8ABB0 to 70757D) allow for a sophisticated hierarchy in secondary information and metadata.

## Typography

The typography is the primary driver of the brand’s premium feel. It uses **Inter** for its systematic, utilitarian precision.

- **Financial Emphasis:** Numbers are treated as hero elements. Large balances use a slightly tighter tracking, while transaction inputs use a dramatic -0.04em letter spacing to create a compact, high-end "digital ticker" aesthetic.
- **Hierarchy:** Section headers and screen titles use semi-bold weights to anchor the page. 
- **Micro-copy:** Metadata and labels utilize the "Muted" color tiers to ensure the primary data remains the focal point.

## Layout & Spacing

This design system employs a **Fluid Grid** with generous safe margins. 

- **Margins:** A standard 24px horizontal margin is maintained across all screens to create a "breathable" frame.
- **Rhythm:** An 8px linear scale guides vertical rhythm. Larger gaps (40px+) are used between distinct content sections to reinforce the minimalist "Polestar" aesthetic.
- **Alignment:** Content is generally left-aligned to mimic editorial layouts, while balance displays are centered for impact.

## Elevation & Depth

In line with the restrained aesthetic, this design system avoids traditional drop shadows. Depth is achieved through **Tonal Layering** and **Low-contrast Outlines**:

- **Stacking:** Elements sit directly on the #FCFCFA background or within #FFFFFF cards.
- **Separation:** A 0.5px or 1px hairline stroke (#E7E8EA) is the primary method of defining boundaries.
- **Active States:** Instead of lifting an element with a shadow, active states are indicated by subtle shifts in background color (e.g., from #FFFFFF to #F6F7F8) or a slight scale reduction (0.98x) upon press.

## Shapes

The shape language is defined by **High-Radius Curvature**, creating a "friendly-tech" juxtaposition against the stark typography.

- **Primary Radius:** Controls, buttons, and input fields use a radius between 22px and 29px, often resulting in a full pill-shape for buttons.
- **Container Radius:** Cards and soft panels follow a consistent 24px radius to match the container padding.
- **Iconography:** Icons are minimal 1.5pt or 2pt line-weight drawings with rounded terminals, mirroring the radius of the UI components.

## Components

- **Buttons:** Primary buttons are solid #080909 with #FFFFFF text. Secondary buttons use #F1F3F5 with #101112 text. All buttons use a 24-29px radius.
- **The Logo:** The 'B' logo must always be rendered as a 1.5px stroke circle with a centered uppercase 'B'.
- **Input Fields:** Minimalist containers using Soft-panel-2 (#F6F7F8) backgrounds. Labels are placed above the field in Muted-3 (#70757D).
- **Cards:** White (#FFFFFF) surfaces with a thin #E7E8EA border. No shadows.
- **Transaction Lists:** Clean rows with 16px vertical padding. Thin 0.5px separators that inset 24px from the left (aligned with text, not the icon).
- **Chips:** Small pill-shaped tags used for categories, utilizing Blue-soft (#DDE8FF) backgrounds with Blue (#075CF2) text.