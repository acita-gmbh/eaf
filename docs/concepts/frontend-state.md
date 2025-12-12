# Frontend State Management

**How we manage data in React without going crazy.**

In the old days of Redux, we treated everything as "Global State." We manually fetched data, dispatched `FETCH_SUCCESS` actions, updated a massive reducer, and then selected it back out. It was boilerplate heaven.

In DVMM, we distinguish between **Server State** and **Client State**.

---

## 1. Server State (TanStack Query)

90% of our "state" is actually just a cache of what's on the server (e.g., the list of VMs, the current user). For this, we use **TanStack Query** (formerly React Query).

### Why?
*   **Automatic Caching:** Fetch once, use everywhere.
*   **Background Refetching:** Keeps data fresh without user action.
*   **Deduplication:** If 5 components ask for the "Current User," we only make 1 network call.
*   **Loading/Error States:** Built-in flags (`isLoading`, `isError`) save us from writing `if (loading) return <Spinner />` logic manually everywhere.

### Example
```tsx
// Custom Hook
export function useVmRequests() {
  return useQuery({
    queryKey: ['vm-requests'],
    queryFn: async () => {
      const response = await api.get('/api/v1/vm-requests');
      return response.data;
    },
    staleTime: 60 * 1000, // Data is "fresh" for 1 minute
  });
}

// Component
function RequestList() {
  const { data, isLoading, error } = useVmRequests();

  if (isLoading) return <Skeleton />;
  if (error) return <ErrorBanner message={error.message} />;

  return (
    <ul>
      {data.map(req => <li key={req.id}>{req.name}</li>)}
    </ul>
  );
}
```

### Optimistic Updates
To make the app feel instant, we update the UI *before* the server confirms the change.
1.  User clicks "Delete".
2.  We immediately remove the item from the list in the cache.
3.  We send the API request.
4.  If it fails, we roll back the change and show an error.

## 2. Client State (React Context / Local State)

This is data that *only* exists in the browser (e.g., "Is the sidebar open?", "What is typed in the search bar?").

*   **Local State (`useState`):** For things isolated to one component (e.g., form input).
*   **Context API:** For global UI state (e.g., Theme, Sidebar visibility, Toast notifications).

We **do not** use Redux. It is overkill for our architecture.

---

## 3. Form State (React Hook Form)

Forms are hard. Validation, dirty states, touched fields... it gets messy. We use **React Hook Form**.

*   **Performance:** Uncontrolled components mean typing doesn't re-render the whole page.
*   **Validation:** Integrates with Zod schema validation.
*   **Simplicity:** `register('fieldName')` is all you need.

```tsx
const { register, handleSubmit } = useForm();
const onSubmit = data => api.post('/vms', data);

<form onSubmit={handleSubmit(onSubmit)}>
  <input {...register("vmName")} />
  <button type="submit">Create</button>
</form>
```

## Summary

| Type of State | Tool |
| :--- | :--- |
| **Server Data** (VMs, Users) | TanStack Query |
| **UI State** (Sidebar, Modals) | React Context |
| **Form State** (Inputs, Validation) | React Hook Form |
| **Complex Global Logic** | *None (Avoided)* |
