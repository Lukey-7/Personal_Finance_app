---
name: Boro
colors:
  surface: '#f9f9fe'
  surface-dim: '#d9dade'
  surface-bright: '#f9f9fe'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f8'
  surface-container: '#ededf2'
  surface-container-high: '#e8e8ed'
  surface-container-highest: '#e2e2e7'
  on-surface: '#1a1c1f'
  on-surface-variant: '#404754'
  inverse-surface: '#2e3034'
  inverse-on-surface: '#f0f0f5'
  outline: '#717785'
  outline-variant: '#c0c6d6'
  surface-tint: '#005eb3'
  primary: '#005baf'
  on-primary: '#ffffff'
  primary-container: '#0074db'
  on-primary-container: '#fefcff'
  inverse-primary: '#a8c8ff'
  secondary: '#5f5e5e'
  on-secondary: '#ffffff'
  secondary-container: '#e2dfde'
  on-secondary-container: '#636262'
  tertiary: '#5c5c5f'
  on-tertiary: '#ffffff'
  tertiary-container: '#747477'
  on-tertiary-container: '#fefcff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d5e3ff'
  primary-fixed-dim: '#a8c8ff'
  on-primary-fixed: '#001b3c'
  on-primary-fixed-variant: '#004689'
  secondary-fixed: '#e5e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1c1b1b'
  on-secondary-fixed-variant: '#474746'
  tertiary-fixed: '#e3e2e5'
  tertiary-fixed-dim: '#c7c6c9'
  on-tertiary-fixed: '#1b1b1e'
  on-tertiary-fixed-variant: '#464649'
  background: '#f9f9fe'
  on-background: '#1a1c1f'
  surface-variant: '#e2e2e7'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '600'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.01em
  title-sm:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 25px
    letterSpacing: -0.005em
  body-base:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: -0.001em
  body-sm:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  container-margin: 20px
  gutter: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
  section-gap: 40px
---

## Brand & Style

The brand identity is centered on precision, high-velocity financial movement, and exclusive simplicity. It targets a sophisticated user base that values efficiency over decorative flair. The aesthetic is rooted in **Minimalism** with a **Corporate Modern** execution, drawing heavily from the Human Interface Guidelines (HIG) to ensure the interface feels like a native extension of the iOS ecosystem.

The visual narrative relies on a "High-Definition" approach: perfectly aligned elements, generous whitespace, and a single, vibrant accent color that signals action and technical reliability. This design system avoids unnecessary ornamentation, using the interplay of light and subtle gray surfaces to create a sense of depth and architectural integrity.

## Colors

The palette is designed to be "light and airy," utilizing a sophisticated range of cool grays to define structure without adding visual weight. 

*   **Primary (#0088FF):** A vibrant, technical blue used exclusively for primary calls to action, active states, and critical data highlights. It serves as the primary "signal" within the interface.
*   **Secondary/Neutral-Dark (#1A1A1A):** Used for primary headings and high-contrast text to ensure maximum legibility.
*   **Surface System:** Backgrounds utilize a pure white (#FFFFFF), while secondary containers and grouping elements use a very soft gray (#F2F2F7).
*   **Functional Colors:** Standard iOS semantic colors for Success (Green), Warning (Amber), and Error (Red) should be desaturated slightly to remain harmonious with the primary accent.

## Typography

This design system utilizes **Inter** across all levels to maintain a systematic, utilitarian aesthetic. The type scale is optimized for mobile readability, following the standard 17pt body size common in premium iOS applications.

To create hierarchy within a minimal framework, the system relies on weight shifts and subtle letter-spacing adjustments rather than size alone. Display styles use a tighter tracking for a more "editorial" feel, while labels utilize increased tracking and uppercase transforms to distinguish secondary information from primary body text.

## Layout & Spacing

The layout philosophy follows a **Fluid Grid** model with fixed horizontal margins of 20px to provide the content with room to breathe, echoing a "premium" sense of space. All internal spacing is built on a 4px baseline grid, with a preference for 8px increments.

Vertical rhythm is strictly maintained through consistent stack units. The 20px margin is a deliberate departure from the standard 16px to create a more spacious, "gallery" feel for financial data. Elements within cards or containers should align to these same margin rules to ensure a cohesive vertical line throughout the scroll.

## Elevation & Depth

Depth in this design system is conveyed primarily through **Tonal Layers** and **Backdrop Blurs**. Rather than using heavy drop shadows, the system uses "Surface-on-Surface" logic:
- **Base Level:** Pure White (#FFFFFF).
- **Secondary Level:** Soft Gray (#F2F2F7) containers.
- **Overlays:** For modals and navigation bars, a high-density background blur (Material Thin) is used to maintain the "airy" feel while providing context.

When shadows are necessary for high-level components like floating action buttons or bottom sheets, they must be **Ambient Shadows**: extremely diffused (20px to 40px blur), low opacity (3-5% alpha), and slightly tinted with a cool gray to avoid a "dirty" look.

## Shapes

The shape language is **Rounded**, mimicking the "continuous corner" or squircle aesthetic of iOS hardware and icons. 

- **Standard Components:** Buttons and input fields use a 12px (0.75rem) radius for a balanced, approachable look.
- **Large Containers:** Cards and bottom sheets utilize a 24px (1.5rem) radius to clearly define major content blocks.
- **Small Elements:** Chips and badges utilize a full pill shape to provide a distinct contrast against the more geometric card structures.

## Components

### Buttons
Primary buttons use the #0088FF background with white text, featuring no border or shadow, relying on the vibrant color for affordance. Secondary buttons should be a subtle gray fill (#E5E5EA) with black text.

### Cards
Cards are defined by a pure white surface against the light gray background, or a very thin 0.5px hairline border (#D1D1D6) when placed on white. They do not use shadows unless they are interactive or draggable.

### Inputs
Input fields follow the "soft surface" approach: a light gray background (#F2F2F7) with no border in their default state. Upon focus, the background remains, but a 1px border of #0088FF is added to signal the active state.

### Lists
Lists are a core part of the fintech experience. They utilize full-width rows with 0.5px dividers that do not extend to the edges (inset dividers), maintaining the native iOS look.

### Additional Components
- **Value Displays:** Large-scale typography for balances, using Medium weight to emphasize the numbers without becoming "heavy."
- **Status Indicators:** Small, circular dots for system health or transaction status, placed adjacent to text labels.
- **Micro-charts:** Sparklines should use the primary accent color (#0088FF) with a subtle 2px stroke width, avoiding fills to keep the "airy" aesthetic.