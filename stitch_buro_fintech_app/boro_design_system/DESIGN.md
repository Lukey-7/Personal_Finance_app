---
name: Boro Design System
colors:
  surface: '#fbf8ff'
  surface-dim: '#dad8e8'
  surface-bright: '#fbf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f2ff'
  surface-container: '#eeecfc'
  surface-container-high: '#e8e6f6'
  surface-container-highest: '#e3e1f1'
  on-surface: '#1a1b26'
  on-surface-variant: '#454558'
  inverse-surface: '#2f2f3b'
  inverse-on-surface: '#f1efff'
  outline: '#757589'
  outline-variant: '#c5c4db'
  surface-tint: '#343dff'
  primary: '#0001bb'
  on-primary: '#ffffff'
  primary-container: '#0000ff'
  on-primary-container: '#b3b7ff'
  inverse-primary: '#bec2ff'
  secondary: '#5f5e5e'
  on-secondary: '#ffffff'
  secondary-container: '#e5e2e1'
  on-secondary-container: '#656464'
  tertiary: '#720001'
  on-tertiary: '#ffffff'
  tertiary-container: '#9d0002'
  on-tertiary-container: '#ffa598'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e0e0ff'
  primary-fixed-dim: '#bec2ff'
  on-primary-fixed: '#00006e'
  on-primary-fixed-variant: '#0000ef'
  secondary-fixed: '#e5e2e1'
  secondary-fixed-dim: '#c9c6c5'
  on-secondary-fixed: '#1c1b1b'
  on-secondary-fixed-variant: '#474646'
  tertiary-fixed: '#ffdad5'
  tertiary-fixed-dim: '#ffb4a9'
  on-tertiary-fixed: '#410000'
  on-tertiary-fixed-variant: '#930002'
  background: '#fbf8ff'
  on-background: '#1a1b26'
  surface-variant: '#e3e1f1'
typography:
  status-time:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 22px
  nav-title:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '650'
    lineHeight: 22px
  small-label:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    letterSpacing: 0.05em
  body-label:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  row-title:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '650'
    lineHeight: 20px
  row-subtitle:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 18px
  row-value:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '500'
    lineHeight: 20px
  input-number:
    fontFamily: Inter
    fontSize: 64px
    fontWeight: '600'
    letterSpacing: -0.055em
  input-suffix:
    fontFamily: Inter
    fontSize: 60px
    fontWeight: '600'
    letterSpacing: -0.055em
  portfolio-balance:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '650'
    letterSpacing: -0.04em
  keypad-number:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 34px
  button-primary:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '650'
    lineHeight: 20px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base_unit: 8px
  horizontal_padding: 24px
  stack_sm: 8px
  stack_md: 16px
  stack_lg: 24px
  stack_xl: 32px
---

## Brand & Style

The visual identity of the design system is anchored in high-utility minimalism, blending the precision of Swiss design with the accessibility of modern iOS patterns. It targets a sophisticated user base that values clarity over decoration, utilizing a monochromatic core punctuated by a singular, high-vibrancy primary blue. 

The aesthetic is characterized by a "Corporate Minimalist" approach: heavy use of whitespace, strict adherence to an 8px grid, and a focus on typographic hierarchy to convey authority and trust. The interface remains flat and structural, relying on generous negative space and subtle tonal shifts rather than complex textures or depth effects.

## Colors

The palette is dominated by a warm-white background that prevents the interface from feeling clinical. The **Primary Blue (#0000FF)** is reserved for high-impact actions and key brand moments, ensuring it retains its functional significance.

- **Foundational Neutrals:** The use of 'Soft' and 'Soft-Blue' provides a subtle way to define containers without the harshness of lines. 
- **Typography Tones:** Three levels of text hierarchy are maintained through 'Text', 'Muted', and 'Muted-Light' to guide the eye across complex financial data.
- **Semantic Indicators:** Green, Red, and Orange are used sparingly for transactional status and market movement, maintaining the system's premium restraint.

## Typography

This design system utilizes **Inter** as a functional equivalent to SF Pro, emphasizing a systematic and neutral tone. The typographic scale is tightly controlled to ensure maximum legibility within the dense information environments of a fintech app.

Special attention is given to numerical data. Input values are oversized with tight tracking (-0.055em) to create a bold, editorial feel during transaction flows. Captions and small labels use uppercase transformations and muted colors to provide clear metadata without competing with primary headings.

## Layout & Spacing

The layout philosophy follows a rigid 8px grid system to maintain mathematical harmony. 

- **Safe Zones:** A mandatory 24px horizontal padding is applied to all screens to ensure content remains comfortably away from the device edges.
- **Grid Rhythm:** Vertically, elements are grouped in multiples of 8px. Use 16px for related content within a card and 32px to separate distinct sections or modules.
- **Alignment:** All content follows a left-aligned vertical axis, creating a strong "spine" for the user's eye to follow, with numerical values right-aligned in row-based layouts for easier comparison.

## Elevation & Depth

This design system avoids traditional drop shadows in favor of **Tonal Layering** and **Low-Contrast Outlines**. 

Depth is communicated through color stacking:
1.  **Level 0 (Background):** #FDFCFB.
2.  **Level 1 (Cards/Containers):** White or #F4F5F6 (Soft).
3.  **Dividers:** #ECEDEF (Line) is used for hairline separators (0.5pt to 1pt) between list items.

Interactive surfaces do not "lift" off the page; instead, they may utilize a subtle background color change on press to maintain the flat, premium aesthetic.

## Shapes

The shape language is defined by "Super-ellipses" and high-radius corners, softening the technical nature of the fintech content. 

Buttons utilize a 25px radius, creating a friendly, pill-like appearance that invites interaction. Large containers and dashboard cards use a 22px radius, ensuring they feel distinct from the smaller UI elements. Pills used for status indicators or filtering toggle between 17px and 21px depending on the content density, maintaining a consistent "stadium" profile across the interface.

## Components

### Buttons
Primary buttons are styled with the Primary Blue (#0000FF) background and white text. They should have a fixed height of 52px or 56px to maintain a premium feel. Secondary buttons use the Soft (#F4F5F6) background with Text (#050505) color.

### Lists & Rows
Rows are the primary data vehicle. Each row features a 40px circular background (typically in Soft #F4F5F6) for icons or currency symbols. The Row Title and Subtitle are stacked on the left, with the Row Value right-aligned. Hairline dividers (#ECEDEF) should be inset by 64px from the left to align with the text, not the icon.

### Inputs
The "Input Number" is the hero of the transaction screen. It should be centered, utilizing the 64px font size. The currency suffix should be placed immediately to the right of the number in Muted color to indicate it is non-editable.

### Cards
Portfolio and balance cards should use the 22px radius. Content inside should be padded by 24px on all sides. For a premium look, use the Soft-Blue (#E8ECFF) as a background for special feature cards or "Pro" tier highlights.

### Iconography & Logo
Icons must be minimal, black-line vectors with a consistent 2px stroke weight. The logo consists of a minimal uppercase 'B' centered within a circular outline, reflecting the "stadium" shape language found throughout the design system.