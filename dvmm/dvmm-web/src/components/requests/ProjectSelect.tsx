import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Progress } from '@/components/ui/progress'
import { MOCK_PROJECTS, type MockProject } from '@/lib/mock-data/projects'
import { NoProjectHelpDialog } from './NoProjectHelpDialog'
import { cn } from '@/lib/utils'

interface ProjectSelectProps {
  value: string
  onValueChange: (value: string) => void
}

/**
 * Project dropdown with quota display
 *
 * Shows available projects with their quota usage.
 * Displays quota progress bar and warning styling when >80% used.
 * Includes "Kein passendes Projekt?" help link.
 *
 * @see AC #3, #4
 */
export function ProjectSelect({ value, onValueChange }: ProjectSelectProps) {
  const selectedProject = MOCK_PROJECTS.find(p => p.id === value)

  return (
    <div className="space-y-2">
      <Select value={value} onValueChange={onValueChange}>
        <SelectTrigger
          className="w-full"
          aria-required="true"
          data-testid="project-select-trigger"
        >
          <SelectValue placeholder="Projekt auswählen..." />
        </SelectTrigger>
        <SelectContent>
          {MOCK_PROJECTS.map(project => (
            <ProjectSelectItem key={project.id} project={project} />
          ))}
        </SelectContent>
      </Select>

      {/* Quota display when project is selected - AC #4 */}
      {selectedProject && (
        <ProjectQuotaDisplay project={selectedProject} />
      )}

      {/* Help link - AC #8 */}
      <NoProjectHelpDialog />
    </div>
  )
}

interface ProjectSelectItemProps {
  project: MockProject
}

function ProjectSelectItem({ project }: ProjectSelectItemProps) {
  const remaining = project.quota.total - project.quota.used

  return (
    <SelectItem value={project.id} data-testid={`project-option-${project.id}`}>
      <div className="flex flex-col gap-0.5">
        <span>{project.name}</span>
        <span className="text-xs text-muted-foreground">
          {remaining} von {project.quota.total} VMs verfügbar
        </span>
      </div>
    </SelectItem>
  )
}

interface ProjectQuotaDisplayProps {
  project: MockProject
}

function ProjectQuotaDisplay({ project }: ProjectQuotaDisplayProps) {
  const quotaPercentage = (project.quota.used / project.quota.total) * 100
  const remaining = project.quota.total - project.quota.used
  const isWarning = quotaPercentage > 80

  return (
    <div className="space-y-1.5" data-testid="project-quota-display">
      <div className="flex justify-between text-sm">
        <span
          className={cn(
            isWarning ? 'text-orange-600 dark:text-orange-400' : 'text-muted-foreground'
          )}
        >
          Verfügbar: {remaining} von {project.quota.total} VMs
        </span>
      </div>
      <Progress
        value={quotaPercentage}
        className={cn(
          'h-2',
          isWarning && '[&>div]:bg-orange-500'
        )}
        aria-label={`Quota usage: ${project.quota.used} of ${project.quota.total} VMs used`}
      />
    </div>
  )
}
