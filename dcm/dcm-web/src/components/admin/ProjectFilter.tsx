import { AlertCircle, RefreshCw } from 'lucide-react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useProjects } from '@/hooks/useProjects'

interface ProjectFilterProps {
  value: string | undefined
  onChange: (projectId: string | undefined) => void
}

/**
 * Dropdown filter for selecting a project.
 *
 * Story 2.9 AC 5: Project filter dropdown
 *
 * Features:
 * - Fetches projects dynamically from backend
 * - "All Projects" default option
 * - Loading skeleton state
 * - Inline error with retry button
 */
export function ProjectFilter({ value, onChange }: Readonly<ProjectFilterProps>) {
  const { data: projects, isLoading, isError, refetch } = useProjects()

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center gap-2" data-testid="project-filter-loading">
        <span className="text-sm text-muted-foreground">Project:</span>
        <Skeleton className="h-9 w-[180px]" />
      </div>
    )
  }

  // Error state with retry
  if (isError) {
    return (
      <div
        className="flex items-center gap-2 text-destructive"
        data-testid="project-filter-error"
      >
        <AlertCircle className="h-4 w-4" />
        <span className="text-sm">Failed to load projects</span>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => refetch()}
          className="h-7 px-2"
          aria-label="Retry loading projects"
        >
          <RefreshCw className="h-3 w-3" />
        </Button>
      </div>
    )
  }

  const handleValueChange = (newValue: string) => {
    // "all" represents no filter
    onChange(newValue === 'all' ? undefined : newValue)
  }

  return (
    <div className="flex items-center gap-2" data-testid="project-filter">
      <span className="text-sm text-muted-foreground">Project:</span>
      <Select
        value={value ?? 'all'}
        onValueChange={handleValueChange}
      >
        <SelectTrigger className="w-[180px]" data-testid="project-filter-trigger">
          <SelectValue placeholder="All Projects" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Projects</SelectItem>
          {projects?.map((project) => (
            <SelectItem key={project.id} value={project.id}>
              {project.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
