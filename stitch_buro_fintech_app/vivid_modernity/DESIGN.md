---
name: Vivid Modernity
colors:
  surface: '#fff8f7'
  surface-dim: '#f4d2d5'
  surface-bright: '#fff8f7'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#fff0f1'
  surface-container: '#ffe9ea'
  surface-container-high: '#ffe1e4'
  surface-container-highest: '#fddadd'
  on-surface: '#291619'
  on-surface-variant: '#5d3e42'
  inverse-surface: '#402b2d'
  inverse-on-surface: '#ffeced'
  outline: '#926e71'
  outline-variant: '#e7bcc0'
  surface-tint: '#bd0044'
  primary: '#b90042'
  on-primary: '#ffffff'
  primary-container: '#e70054'
  on-primary-container: '#fffbff'
  inverse-primary: '#ffb2bb'
  secondary: '#b22548'
  on-secondary: '#ffffff'
  secondary-container: '#fd5f7c'
  on-secondary-container: '#63001f'
  tertiary: '#006a3c'
  on-tertiary: '#ffffff'
  tertiary-container: '#00864e'
  on-tertiary-container: '#f6fff5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffd9dc'
  primary-fixed-dim: '#ffb2bb'
  on-primary-fixed: '#400011'
  on-primary-fixed-variant: '#910032'
  secondary-fixed: '#ffd9dc'
  secondary-fixed-dim: '#ffb2ba'
  on-secondary-fixed: '#400011'
  on-secondary-fixed-variant: '#900332'
  tertiary-fixed: '#84fab1'
  tertiary-fixed-dim: '#67dd97'
  on-tertiary-fixed: '#00210f'
  on-tertiary-fixed-variant: '#00522d'
  background: '#fff8f7'
  on-background: '#291619'
  surface-variant: '#fddadd'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1.2'
    letterSpacing: 0.02em
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
---

# Design System: Vivid Modernity

## Brand & Style
This design system embodies a "High-Contrast / Modern" aesthetic. It transitions from a previous industrial energy to a more vibrant, digitally-native personality. By pairing a punchy Raspberry primary tone with the functional clarity of the Inter typeface, the brand strikes a balance between expressive energy and professional utility. The visual language is high-energy, confident, and clean, designed to stand out in a crowded digital landscape while maintaining rigorous usability standards.

## Colors
The palette is anchored by an electric Raspberry primary color (`#ff005e`), which serves as the main driver for calls to action and brand identification. The secondary color is a sophisticated rose-tinted neutral that supports the primary without competing for attention. 

The tertiary color has shifted to a vibrant green (`#009557`), providing a crisp contrast used for success states or distinct highlighting. The neutral palette is grounded in warm, slightly red-tinted greys to ensure that even the background elements feel cohesive with the warm primary tones. 

## Typography
The system uses "Inter" across all levels to provide a highly legible, geometric, and modern feel. Headlines utilize a heavier weight (Bold/Semi-bold) to anchor the page, while body text leverages Inter's excellent x-height for long-form readability. Labels and utility text use medium weights with slight letter spacing to ensure clarity at small scales.

## Layout & Spacing
The layout relies on a fluid grid system with a standard 8px spacing rhythm. This ensures mathematical harmony across all components. Margins and gutters are consistently applied to maintain a clean vertical rhythm. Information density is kept moderate, allowing the vibrant color palette enough "white space" to breathe without overwhelming the user.

## Elevation & Depth
Depth is communicated through tonal layering and soft ambient shadows. Rather than harsh outlines, surfaces use subtle shifts in background color and diffused, low-opacity shadows to indicate hierarchy. Higher elevation levels (like modals or floating buttons) feature slightly more pronounced shadows with a hint of the neutral-warm tint to keep the depth feeling natural.

## Shapes
The design has moved away from sharp edges to a "Rounded" shape language. Standard components like buttons and input fields feature a 0.5rem (8px) corner radius. Larger containers like cards utilize a 1rem radius. This change softens the high-contrast color palette, making the overall interface feel more approachable and contemporary.

## Components
- **Buttons:** Feature the primary Raspberry color with white text. They use the standard 8px rounded corners and a subtle lift effect on hover.
- **Input Fields:** Utilize the warm neutral-grey for borders, moving to the primary color for active focus states.
- **Cards:** Defined by soft 1rem rounded corners and low-opacity ambient shadows to separate content from the background.
- **Chips & Labels:** Use light tints of the primary or tertiary colors to categorize information without adding heavy visual weight.