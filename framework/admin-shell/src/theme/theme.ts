import { createTheme } from '@mui/material/styles';
import type { Theme } from '@mui/material';

/**
 * EAF Admin Shell - Axians Branded Theme
 *
 * Design Tokens from UX Spec (docs/ux/story-7.4-frontend-spec-supplement.md):
 * - Primary: #0066CC (Axians blue)
 * - Typography: Roboto font family
 * - Spacing: 8px grid
 * - Border radius: 8px
 * - Responsive breakpoints: mobile (768px), tablet (1024px), desktop (1440px)
 */
export const eafTheme: Theme = createTheme({
  palette: {
    primary: {
      main: '#0066CC', // Axians primary blue
      light: '#3384D6',
      dark: '#004C99',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: '#5C6F82',
      light: '#7A8B9B',
      dark: '#3F4F5E',
      contrastText: '#FFFFFF',
    },
    error: {
      main: '#DC3545',
      light: '#E4606D',
      dark: '#C82333',
    },
    warning: {
      main: '#FFC107',
      light: '#FFD54F',
      dark: '#FFA000',
    },
    success: {
      main: '#28A745',
      light: '#5CB85C',
      dark: '#1E7E34',
    },
    background: {
      default: '#F5F5F5',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#212121',
      secondary: '#757575',
      disabled: '#BDBDBD',
    },
    divider: '#E0E0E0',
  },

  typography: {
    fontFamily: 'Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif',
    h1: {
      fontSize: '2rem',      // 32px
      fontWeight: 700,
      lineHeight: 1.25,      // 40px
    },
    h2: {
      fontSize: '1.5rem',    // 24px
      fontWeight: 500,
      lineHeight: 1.33,      // 32px
    },
    h3: {
      fontSize: '1.25rem',   // 20px
      fontWeight: 500,
      lineHeight: 1.4,       // 28px
    },
    body1: {
      fontSize: '1rem',      // 16px
      lineHeight: 1.5,       // 24px
    },
    body2: {
      fontSize: '0.875rem',  // 14px
      lineHeight: 1.43,      // 20px
    },
    caption: {
      fontSize: '0.75rem',   // 12px
      lineHeight: 1.33,      // 16px
    },
  },

  shape: {
    borderRadius: 8, // 8px subtle rounding
  },

  spacing: 8, // 8px baseline grid

  breakpoints: {
    values: {
      xs: 0,
      sm: 768,    // Tablet
      md: 1024,   // Desktop
      lg: 1440,   // Wide desktop
      xl: 1920,
    },
  },

  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none', // Preserve casing (no uppercase transform)
          borderRadius: 8,
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 8,
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          borderRadius: 8,
        },
      },
    },
  },
});

export default eafTheme;
