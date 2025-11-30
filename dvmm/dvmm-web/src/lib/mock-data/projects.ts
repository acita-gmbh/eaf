/**
 * Mock project data for VM Request Form
 *
 * Will be replaced with real data from ProjectQueryService in Story 2.6+
 */
export interface MockProject {
  id: string
  name: string
  quota: {
    used: number
    total: number
  }
}

export const MOCK_PROJECTS: MockProject[] = [
  { id: 'proj-1', name: 'Entwicklung', quota: { used: 5, total: 10 } },
  { id: 'proj-2', name: 'Produktion', quota: { used: 8, total: 10 } },
  { id: 'proj-3', name: 'Testing', quota: { used: 0, total: 5 } },
  { id: 'proj-4', name: 'Legacy', quota: { used: 9, total: 10 } }, // 90% - warning state
]
