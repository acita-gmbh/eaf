# Story 7.4: React-Admin Portal - Frontend Specification Supplement

**Parent Document**: [docs/front-end-spec.md](../front-end-spec.md)
**Purpose**: Detailed frontend specifications for React-Admin 5.4.0 resource templates
**Created**: 2025-10-03
**UX Expert**: Sally 🎨
**Status**: Supplement to existing front-end-spec.md

---

## Introduction

This document supplements the main EAF UI/UX Specification with **specific implementation details for Story 7.4's React-Admin resource generator**. It provides component-level specifications, interaction patterns, and accessibility requirements for the generated List, Create, Edit, and Show templates.

**Scope**: React-Admin 5.4.0 CRUD resource templates (Products, Licenses, Tenants, Users)

**Related Documents**:
- Main Frontend Spec: [docs/front-end-spec.md](../front-end-spec.md)
- UX Review: [docs/ux/story-7.4-ux-review.md](./story-7.4-ux-review.md)
- Story Definition: [docs/stories/7.4.create-react-admin-generator.story.md](../stories/7.4.create-react-admin-generator.story.md)

---

## Component Specifications

### 1. List View Component Template

**File**: `apps/admin/src/resources/{resourceLowerCase}/List.tsx`

**Visual Hierarchy**:
```
┌─────────────────────────────────────────────────────────┐
│ [Breadcrumbs: Dashboard > Products]                    │
├─────────────────────────────────────────────────────────┤
│ Products                                [+ Create]  [⋮] │
├─────────────────────────────────────────────────────────┤
│ [🔍 Search]  [Filter: ▼ Status]  [Export CSV]          │
├─────────────────────────────────────────────────────────┤
│ □ │ ID      │ Name     │ Price    │ Status │ Actions │
│ □ │ prod-1  │ Widget A │ $99.00   │ Active │ [Edit]  │
│ □ │ prod-2  │ Widget B │ $149.00  │ Active │ [Edit]  │
│ □ │ prod-3  │ Legacy   │ $49.00   │ Disc.  │ [Edit]  │
├─────────────────────────────────────────────────────────┤
│ Showing 1-25 of 150        [←] [1] [2] [3] [→]        │
└─────────────────────────────────────────────────────────┘
```

**React-Admin Template**:
```tsx
export const {ResourceName}List = () => (
  <List
    filters={<{ResourceName}Filters />}
    perPage={25}
    sort={{ field: 'createdAt', order: 'DESC' }}
    empty={<EmptyState />}
  >
    <Datagrid
      bulkActionButtons={<BulkDeleteWithConfirmButton />}
      optimized
      sx={{ '& .RaDatagrid-headerCell': { fontWeight: 600 } }}
    >
      <TextField source="id" label="ID" sortable={false} />
      <TextField source="name" label="Name" />
      <NumberField source="price" label="Price"
        options={{ style: 'currency', currency: 'USD' }} />
      <BooleanField source="active" label="Active" />
      <DateField source="createdAt" label="Created" showTime />
      <EditButton />
    </Datagrid>
  </List>
);

// Filter sidebar
const {ResourceName}Filters = [
  <TextInput source="q" label="Search" alwaysOn />,
  <SelectInput source="status" label="Status" choices={[
    { id: 'active', name: 'Active' },
    { id: 'inactive', name: 'Inactive' }
  ]} />,
];

// Empty state
const EmptyState = () => (
  <Box textAlign="center" p={4}>
    <Typography variant="h6" gutterBottom>
      No {resources} yet
    </Typography>
    <Typography color="text.secondary" paragraph>
      Get started by creating your first {resource}.
    </Typography>
    <Button variant="contained" component={Link} to="/{resourceLowerCase}/create">
      Create First {ResourceName}
    </Button>
  </Box>
);
```

**Accessibility Requirements**:
- Table has `role="table"` with `aria-label="{ResourceName} list"`
- Column headers have `aria-sort` attributes (ascending/descending/none)
- Row selection checkboxes have `aria-label="Select {resourceName}"`
- Pagination has `aria-label="Pagination navigation"`
- Filter sidebar has `role="search"` landmark

**Responsive Behavior** (Tablet 768-1023px):
```tsx
// Mobile/Tablet card layout (replaces table)
<List>
  <SimpleList
    primaryText={(record) => record.name}
    secondaryText={(record) => `$${record.price}`}
    tertiaryText={(record) => record.status}
    linkType="show"
  />
</List>
```

---

### 2. Create Form Component Template

**File**: `apps/admin/src/resources/{resourceLowerCase}/Create.tsx`

**Visual Layout**:
```
┌─────────────────────────────────────────────────────────┐
│ [← Back to List]                                        │
├─────────────────────────────────────────────────────────┤
│ Create {ResourceName}                                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ┌─ Basic Information ──────────────────────────────┐   │
│ │                                                   │   │
│ │ SKU *             [________]                      │   │
│ │ Format: AAA-######                                │   │
│ │                                                   │   │
│ │ Name *            [________]                      │   │
│ │                                                   │   │
│ │ Description       [________]                      │   │
│ │                   [        ]                      │   │
│ │                                                   │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ ┌─ Pricing & Status ───────────────────────────────┐   │
│ │                                                   │   │
│ │ Price (USD) *     [________]                      │   │
│ │                                                   │   │
│ │ Active            [Toggle: ON]                    │   │
│ │                                                   │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│                              [Cancel]  [Save]          │
└─────────────────────────────────────────────────────────┘
```

**React-Admin Template**:
```tsx
export const {ResourceName}Create = () => (
  <Create>
    <SimpleForm>
      <Typography variant="h6" sx={{ mb: 2 }}>Basic Information</Typography>
      <TextInput
        source="sku"
        label="SKU"
        validate={[required(), regex(/^[A-Z]{3}-[0-9]{6}$/, 'Must be format AAA-######')]}
        helperText="Product SKU in format AAA-######"
        fullWidth
      />
      <TextInput
        source="name"
        label="Name"
        validate={required()}
        fullWidth
      />
      <TextInput
        source="description"
        label="Description"
        multiline
        rows={4}
        helperText="Customer-facing description (max 500 chars)"
        fullWidth
      />

      <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>Pricing & Status</Typography>
      <NumberInput
        source="price"
        label="Price (USD)"
        validate={[required(), minValue(0)]}
        helperText="Price in cents (100 = $1.00)"
      />
      <BooleanInput
        source="active"
        label="Active"
        defaultValue={true}
      />
    </SimpleForm>
  </Create>
);
```

**Validation Strategy**:
- **Client-Side**: React-Admin validators (required, regex, minValue) run on blur and submit
- **Server-Side**: API returns 400 with RFC 7807 Problem Details → Show inline field errors
- **Error Display**: Field errors appear below input with red text, input border turns red

**Accessibility Requirements**:
- Form has `<form>` element with `aria-labelledby` pointing to page title
- All inputs have associated `<label>` elements
- Required fields have `aria-required="true"`
- Error messages have `aria-live="polite"` for screen reader announcement
- Submit button disabled during API call with `aria-busy="true"`

**Auto-Save Draft** (Optional Enhancement - Story 7.5):
```tsx
// Save form state to localStorage every 30 seconds
useEffect(() => {
  const interval = setInterval(() => {
    localStorage.setItem('draft_{resourceLowerCase}', JSON.stringify(form.getState().values));
  }, 30000);
  return () => clearInterval(interval);
}, [form]);
```

---

### 3. Edit Form Component Template

**File**: `apps/admin/src/resources/{resourceLowerCase}/Edit.tsx`

**Visual Layout** (same as Create + Delete button):
```
┌─────────────────────────────────────────────────────────┐
│ [← Back to List]                                        │
├─────────────────────────────────────────────────────────┤
│ Edit {ResourceName}: {record.name}                      │
├─────────────────────────────────────────────────────────┤
│ [Delete]                           [Cancel]  [Save]    │
│                                                         │
│ [Form fields identical to Create...]                   │
└─────────────────────────────────────────────────────────┘
```

**React-Admin Template**:
```tsx
export const {ResourceName}Edit = () => (
  <Edit mutationMode="optimistic">
    <SimpleForm
      toolbar={<EditToolbar />}
    >
      {/* Same fields as Create */}
    </SimpleForm>
  </Edit>
);

const EditToolbar = () => (
  <Toolbar sx={{ justifyContent: 'space-between' }}>
    <DeleteButton
      confirmTitle="Delete {ResourceName}?"
      confirmContent="Type the {resource} name to confirm deletion: "
      mutationOptions={{
        onSuccess: () => notify('{ResourceName} deleted', { type: 'info', undoable: true })
      }}
    />
    <Box>
      <SaveButton />
    </Box>
  </Toolbar>
);
```

**Optimistic Update Pattern**:
- User clicks Save → Form immediately shows success state
- API call happens in background
- If API returns 200 → Success notification, no change needed
- If API returns 400/500 → Revert form values, show error

**Dirty Form Detection**:
```tsx
// Warn user if navigating away with unsaved changes
const { formState } = useFormContext();
useEffect(() => {
  const handleBeforeUnload = (e: BeforeUnloadEvent) => {
    if (formState.isDirty) {
      e.preventDefault();
      e.returnValue = 'You have unsaved changes. Leave anyway?';
    }
  };
  window.addEventListener('beforeunload', handleBeforeUnload);
  return () => window.removeEventListener('beforeunload', handleBeforeUnload);
}, [formState.isDirty]);
```

**Delete Confirmation UX**:
- Click Delete → Modal dialog appears
- Dialog shows: "Type '{resourceName}' to confirm deletion"
- User must type exact name (case-sensitive)
- Only then is Delete button enabled
- After deletion → Undo snackbar for 5 seconds

---

### 4. Show (Detail) Component Template

**File**: `apps/admin/src/resources/{resourceLowerCase}/Show.tsx`

**Visual Layout**:
```
┌─────────────────────────────────────────────────────────┐
│ [← Back to List]                        [Edit] [Delete] │
├─────────────────────────────────────────────────────────┤
│ {ResourceName}: {record.name}                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ━━ Basic Information ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                         │
│ ID                 prod-12345-abc                       │
│ SKU                ABC-123456                           │
│ Name               Enterprise Widget                    │
│ Description        High-performance widget for...       │
│                                                         │
│ ━━ Pricing & Status ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                         │
│ Price              $99.00                               │
│ Active             ✓ Active                             │
│                                                         │
│ ━━ Metadata ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                         │
│ Created At         2025-10-01 14:30:15                  │
│ Updated At         2025-10-03 09:15:42                  │
│ Tenant ID          tenant-abc-123 (Admin only)          │
│                                                         │
│ ━━ Related Licenses (3) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                         │
│ │ License Key    │ Customer      │ Expires    │        │
│ │ LIC-ABC-123    │ Acme Corp     │ 2026-01-01 │        │
│ │ LIC-DEF-456    │ TechCo        │ 2025-12-15 │        │
│ │ LIC-GHI-789    │ StartupXYZ    │ 2025-11-30 │        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**React-Admin Template**:
```tsx
export const {ResourceName}Show = () => (
  <Show>
    <SimpleShowLayout>
      <Typography variant="h6" sx={{ mt: 2, mb: 1, color: 'primary.main' }}>
        Basic Information
      </Typography>
      <TextField source="id" label="ID" />
      <TextField source="sku" label="SKU" />
      <TextField source="name" label="Name" />
      <TextField source="description" label="Description" />

      <Typography variant="h6" sx={{ mt: 3, mb: 1, color: 'primary.main' }}>
        Pricing & Status
      </Typography>
      <NumberField source="price" label="Price"
        options={{ style: 'currency', currency: 'USD' }} />
      <BooleanField source="active" label="Active Status" />

      <Typography variant="h6" sx={{ mt: 3, mb: 1, color: 'primary.main' }}>
        Metadata
      </Typography>
      <DateField source="createdAt" label="Created At" showTime />
      <DateField source="updatedAt" label="Last Updated" showTime />

      {/* Show tenant ID only for admins */}
      <FunctionField
        label="Tenant ID"
        render={(record) =>
          hasRole('ROLE_eaf-admin') ? record.tenantId : null
        }
      />

      <Typography variant="h6" sx={{ mt: 3, mb: 1, color: 'primary.main' }}>
        Related Licenses
      </Typography>
      <ReferenceManyField
        label={false}
        reference="licenses"
        target="productId"
      >
        <Datagrid>
          <TextField source="licenseKey" label="License Key" />
          <TextField source="customerName" label="Customer" />
          <DateField source="expiresAt" label="Expires" />
          <ShowButton />
        </Datagrid>
      </ReferenceManyField>
    </SimpleShowLayout>
  </Show>
);
```

**Copy-to-Clipboard Feature** (UX Enhancement):
```tsx
// Add copy button for UUID fields
<FunctionField
  source="id"
  label="ID"
  render={(record) => (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Typography>{record.id}</Typography>
      <IconButton
        size="small"
        onClick={() => {
          navigator.clipboard.writeText(record.id);
          notify('ID copied to clipboard', { type: 'info' });
        }}
        aria-label="Copy ID to clipboard"
      >
        <ContentCopyIcon fontSize="small" />
      </IconButton>
    </Box>
  )}
/>
```

---

## Interaction Patterns

### Loading States

**List View Loading**:
```tsx
// Show skeleton rows while data fetches
{loading && (
  <Box>
    {[...Array(5)].map((_, i) => (
      <Skeleton key={i} variant="rectangular" height={48} sx={{ my: 1 }} />
    ))}
  </Box>
)}
```

**Form Submission Loading**:
```tsx
<SaveButton
  label="Saving..."
  icon={<CircularProgress size={20} />}
  disabled={loading}
/>
```

### Error Handling

**RFC 7807 Problem Details Mapper**:
```tsx
// Custom data provider error handler
const dataProvider = {
  // ... other methods

  handleError: (error) => {
    if (error.body?.type?.includes('/errors/')) {
      return {
        message: error.body.detail || 'Operation failed',
        caption: error.body.traceId
          ? `Error ID: ${error.body.traceId} (copy for support)`
          : undefined,
      };
    }
    return { message: 'An unexpected error occurred. Please try again.' };
  },
};
```

**Error Notification Display**:
```tsx
<Notification
  message={error.message}
  caption={error.caption}
  severity="error"
  role="alert"
  aria-live="assertive"
/>
```

### Bulk Actions

**Bulk Delete with Type-to-Confirm**:
```tsx
const BulkDeleteWithConfirmButton = () => {
  const [confirmText, setConfirmText] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const { selectedIds, onUnselectItems } = useListContext();

  const handleBulkDelete = () => {
    if (confirmText === 'DELETE') {
      // Perform bulk delete
      dataProvider.deleteMany('products', { ids: selectedIds });
      setDialogOpen(false);
      onUnselectItems();
      notify(`${selectedIds.length} items deleted`, { type: 'info', undoable: true });
    }
  };

  return (
    <>
      <Button onClick={() => setDialogOpen(true)}>
        Delete {selectedIds.length} items
      </Button>
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
        <DialogTitle>Delete {selectedIds.length} items?</DialogTitle>
        <DialogContent>
          <Typography gutterBottom>
            This action cannot be undone. Type "DELETE" to confirm.
          </Typography>
          <TextField
            fullWidth
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="Type DELETE"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleBulkDelete}
            color="error"
            disabled={confirmText !== 'DELETE'}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
```

---

## Accessibility Implementation

### ARIA Attributes by Component

**List View**:
```tsx
<List aria-label="{ResourceName} list">
  <Datagrid
    aria-label="{ResourceName} table"
    aria-rowcount={total}
  >
    <TextField source="name"
      headerCellProps={{ 'aria-sort': sortField === 'name' ? sortOrder : 'none' }}
    />
  </Datagrid>
</List>
```

**Form Inputs**:
```tsx
<TextInput
  source="sku"
  label="SKU"
  aria-label="Product SKU"
  aria-required="true"
  aria-invalid={!!errors.sku}
  aria-describedby="sku-helper-text sku-error"
/>
<FormHelperText id="sku-helper-text">
  Format: AAA-######
</FormHelperText>
{errors.sku && (
  <FormHelperText id="sku-error" role="alert">
    {errors.sku.message}
  </FormHelperText>
)}
```

**Notifications**:
```tsx
<Snackbar
  open={notification.open}
  message={notification.message}
  role="status"
  aria-live="polite"
  aria-atomic="true"
/>
```

### Keyboard Shortcuts

**List View**:
- `Tab` / `Shift+Tab`: Navigate between table cells
- `Enter` / `Space`: Activate button/checkbox in focused cell
- `Arrow Up/Down`: Navigate between rows (when row is focused)
- `c`: Quick create (opens Create form)
- `/`: Focus search input

**Form**:
- `Tab` / `Shift+Tab`: Navigate between form fields
- `Enter`: Submit form (when focus in text input)
- `Escape`: Cancel form (return to list)

---

## Performance Optimization

### Code Splitting

```tsx
// Lazy load resource components
const ProductList = lazy(() => import('./resources/product/List'));
const ProductCreate = lazy(() => import('./resources/product/Create'));
const ProductEdit = lazy(() => import('./resources/product/Edit'));
const ProductShow = lazy(() => import('./resources/product/Show'));

// In App.tsx
<Resource
  name="products"
  list={ProductList}
  create={ProductCreate}
  edit={ProductEdit}
  show={ProductShow}
/>
```

### Virtual Scrolling for Large Lists

```tsx
<List perPage={25}>
  <Datagrid optimized> {/* Enables react-window virtual scrolling */}
    {/* Fields */}
  </Datagrid>
</List>
```

### Request Debouncing

```tsx
// Debounce search input to reduce API calls
<TextInput
  source="q"
  label="Search"
  alwaysOn
  resettable
  debounce={500} // Wait 500ms after user stops typing
/>
```

---

## Visual Design Tokens

### Material-UI Theme Override

```tsx
// apps/admin/src/theme.ts
export const theme = createTheme({
  palette: {
    primary: {
      main: '#0066CC', // Axians primary blue
      light: '#3384D6',
      dark: '#004C99',
    },
    secondary: {
      main: '#5C6F82',
      light: '#7A8B9B',
      dark: '#3F4F5E',
    },
    error: {
      main: '#DC3545',
    },
    warning: {
      main: '#FFC107',
    },
    success: {
      main: '#28A745',
    },
  },
  typography: {
    fontFamily: 'Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif',
    h1: { fontSize: '2rem', fontWeight: 700 },
    h2: { fontSize: '1.5rem', fontWeight: 500 },
    h3: { fontSize: '1.25rem', fontWeight: 500 },
    body1: { fontSize: '1rem', lineHeight: 1.5 },
    body2: { fontSize: '0.875rem', lineHeight: 1.43 },
  },
  shape: {
    borderRadius: 8,
  },
  spacing: 8, // 8px grid
});
```

### Component-Specific Styling

```tsx
// Table header styling
const tableHeaderStyle = {
  '& .RaDatagrid-headerCell': {
    fontWeight: 600,
    backgroundColor: '#F5F5F5',
    borderBottom: '2px solid #E0E0E0',
  },
};

// Form section heading
const sectionHeadingStyle = {
  mt: 3,
  mb: 2,
  color: 'primary.main',
  borderBottom: '1px solid',
  borderColor: 'divider',
  pb: 1,
};
```

---

## Next Steps

### Implementation Checklist (Story 7.4)

- [ ] Create 5 Mustache templates (List, Create, Edit, Show, types.ts)
- [ ] Include all accessibility ARIA attributes in templates
- [ ] Add loading skeleton components
- [ ] Implement RFC 7807 error mapper in data provider
- [ ] Add bulk delete with type-to-confirm
- [ ] Create empty state components for all resources
- [ ] Test keyboard navigation (all features accessible via keyboard)
- [ ] Validate WCAG AA compliance with Axe DevTools

### Future UX Enhancements (Story 7.5)

- [ ] Auto-save drafts to localStorage
- [ ] Column show/hide customization for DataGrid
- [ ] Advanced filtering (date ranges, multi-select)
- [ ] Export to CSV/Excel functionality
- [ ] Audit log viewer (track all CRUD operations)
- [ ] Dark mode support
- [ ] Inline editing (edit fields directly in table)
- [ ] Customizable dashboard widgets

---

**Document Status**: READY FOR IMPLEMENTATION
**Handoff**: Ready for Dev Agent (James) to implement Story 7.4 templates
**Review**: Requires Product Owner Sarah approval before implementation begins

**UX Expert**: Sally 🎨
**Date**: 2025-10-03
