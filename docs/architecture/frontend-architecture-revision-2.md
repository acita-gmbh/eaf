# Frontend Architecture (Revision 2)

  * **Component Architecture:** React-Admin/MUI components in `apps/admin/src/`.
  * **State Management:** Zustand + React Context.
  * **Routing:** React Router (via React-Admin `<Resource>` components), using `React.lazy()` for all routes.
  * **Frontend Services Layer (Critical Requirement):** Replaces the standard REST data provider. Must use a **Push-Based (WebSocket) strategy**. The Axon Projection (Backend) publishes to Redis Pub/Sub, the EAF server pushes this via WebSocket, and the React-Admin UI listens for this push notification (e.g., `PROJECTION_UPDATED`) to trigger a data refresh. This avoids polling and correctly handles our asynchronous `202 Accepted` CQRS response.

-----
