import { Box, Typography, Button } from '@mui/material';
import { Link } from 'react-admin';
import type { ComponentType } from 'react';

export interface EmptyStateProps {
  /** Message to display */
  message: string;
  /** Optional icon component */
  icon?: ComponentType;
  /** Optional CTA button label */
  ctaLabel?: string;
  /** Optional CTA button link */
  ctaLink?: string;
}

/**
 * Empty state component for lists with no data
 *
 * Features:
 * - Helpful message explaining no data
 * - Optional icon (Material-UI icon component)
 * - Optional CTA button to create first item
 * - WCAG AA accessible (adequate contrast, semantic HTML)
 *
 * Usage:
 * ```tsx
 * <List empty={<EmptyState message="No products yet" ctaLabel="Create First Product" ctaLink="/products/create" />}>
 *   <Datagrid>...</Datagrid>
 * </List>
 * ```
 */
export const EmptyState = ({ message, icon: Icon, ctaLabel, ctaLink }: EmptyStateProps) => {
  return (
    <Box
      textAlign="center"
      p={4}
      role="status"
      aria-live="polite"
      aria-label="Empty state"
    >
      {Icon && (
        <Box sx={{ fontSize: 80, color: 'text.secondary', mb: 2 }}>
          <Icon />
        </Box>
      )}

      <Typography variant="h6" gutterBottom>
        {message}
      </Typography>

      {ctaLabel && ctaLink && (
        <Button
          variant="contained"
          component={Link}
          to={ctaLink}
          sx={{ mt: 2 }}
          aria-label={ctaLabel}
        >
          {ctaLabel}
        </Button>
      )}
    </Box>
  );
};
