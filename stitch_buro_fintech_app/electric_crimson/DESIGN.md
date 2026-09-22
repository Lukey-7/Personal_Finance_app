---
name: Electric Crimson
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
  on-surface-variant: '#414753'
  inverse-surface: '#313030'
  inverse-on-surface: '#f3f0ef'
  outline: '#717785'
  outline-variant: '#c1c6d5'
  surface-tint: '#bd0044'
  primary: '#bd0043'
  on-primary: '#ffffff'
  primary-container: '#eb0056'
  on-primary-container: '#130002'
  inverse-primary: '#ffb2bb'
  secondary: '#595f67'
  on-secondary: '#ffffff'
  secondary-container: '#dde3ed'
  on-secondary-container: '#5f656d'
  tertiary: '#5c5e60'
  on-tertiary: '#ffffff'
  tertiary-container: '#757779'
  on-tertiary-container: '#030506'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffd9dc'
  primary-fixed-dim: '#ffb2bb'
  on-primary-fixed: '#400011'
  on-primary-fixed-variant: '#910032'
  secondary-fixed: '#dde3ed'
  secondary-fixed-dim: '#c1c7d0'
  on-secondary-fixed: '#161c23'
  on-secondary-fixed-variant: '#41474f'
  tertiary-fixed: '#e1e2e4'
  tertiary-fixed-dim: '#c5c6c8'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#fcf8f8'
  on-background: '#1c1b1b'
  surface-variant: '#e5e2e1'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin: 24px
---

# Design System: Electric Crimson

## Brand & Style
The brand personality is energetic, bold, and modern. By moving from a conservative blue to a vibrant magenta-pink and high-contrast neutrals, the UI evokes a sense of dynamism and creative edge. The target audience includes tech-forward users and creators who value high visibility and a distinct, punchy aesthetic.

The design style follows a **Corporate / Modern** aesthetic with a high-contrast twist. It prioritizes functional balance, clear information hierarchy, and a clean, approachable look that minimizes cognitive load while maintaining a sophisticated, high-energy edge.

## Colors
The color palette is built on a foundation of high-contrast neutrals punctuated by a high-energy primary accent.

- **Primary:** A vibrant magenta (#FF005E) used for primary actions, critical states, and core branding. It is designed to stand out sharply against both light and dark backgrounds.
- **Secondary:** A cool, medium gray (#959BA4) used for supportive UI elements and balanced tonal transitions.
- **Tertiary:** An off-white/very light gray (#F2F3F5) used for subtle container backgrounds and low-priority surface differentiation.
- **Neutral:** A deep, near-black (#050505) used for primary text, structural borders, and high-emphasis icons.

The system operates in a **light color mode**, ensuring high readability and a crisp, high-contrast environment.

## Typography
The system utilizes **Inter** across all levels of the hierarchy. Inter is chosen for its exceptional legibility on digital screens and its neutral, contemporary character.

- **Headlines:** Use Inter with Bold or Semi-bold weights (e.g., 32px for Large Headlines) to establish clear hierarchy.
- **Body Text:** Uses Inter Regular (14px-16px) with comfortable line heights for optimal readability.
- **Labels:** Use Inter Medium (12px) with slight letter spacing to remain clear at small sizes.

## Layout & Spacing
The layout follows a **fluid grid** system designed to adapt to various screen sizes. A 12-column structure is used for desktop environments, transitioning to simpler stacked layouts for mobile.

The spacing rhythm is built on a base unit of 8px. This creates a consistent 8pt grid that governs margins, paddings, and component heights, ensuring a mathematical harmony across the interface. Gutters are typically set to 16px, with outer margins at 24px.

## Elevation & Depth
Depth is conveyed through **tonal layers** and **ambient shadows**. Given the high-contrast nature of the palette, depth is used to soften the transition between the vibrant primary colors and neutral surfaces. 

Elements higher in the stack, such as modals, feature more diffused shadows. Cards and containers use low-altitude shadows or simple neutral outlines to provide structural definition against the background.

## Shapes
The shape language is defined as **Rounded**. By using a standard corner radius of 8px (0.5rem), the UI feels approachable and modern, balancing the aggressive color palette with friendly geometry.

- **Standard components (Buttons, Inputs):** 8px radius.
- **Large containers (Cards):** 16px radius.
- **Extra large elements:** 24px radius.

This consistency in curvature softens the bold magenta palette and ensures a cohesive visual rhythm.

## Components
- **Buttons:** Feature a primary magenta fill (#FF005E) with 8px rounded corners. Text is white for high contrast. Secondary buttons use the gray tone (#959BA4) or a neutral black outline.
- **Chips:** Small, highly rounded elements used for tags; the tertiary light gray (#F2F3F5) is used for backgrounds to keep them subtle.
- **Cards:** White or tertiary-gray backgrounds with 16px corner radius and a soft ambient shadow to define elevation.
- **Input Fields:** Neutral borders that transition to a primary magenta focus ring when active.
- **Checkboxes & Radios:** Use the primary magenta for active states, following the established rounding logic.