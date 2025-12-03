import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  ArrowLeft,
  AlertCircle,
  Cpu,
  MemoryStick,
  HardDrive,
  FileText,
  User,
  Mail,
  Briefcase,
  History,
  FolderKanban,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { StatusBadge } from '@/components/requests/StatusBadge'
import { Timeline } from '@/components/requests/Timeline'
import { useAdminRequestDetail } from '@/hooks/useAdminRequestDetail'
import { formatDateTime, formatRelativeTime } from '@/lib/date-utils'

/**
 * Admin page displaying detailed VM request information.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Features:
 * - AC 1: Page loads with correct request details
 * - AC 2: Requester Information (name, email, role)
 * - AC 3: Request Details (VM specs, justification)
 * - AC 4: Project context placeholder (quota in Epic 4)
 * - AC 5: Timeline events
 * - AC 6: Requester History (up to 5 recent requests)
 * - AC 7: Approve/Reject buttons disabled (Story 2.11)
 * - AC 8: Back button navigation
 * - AC 9: Loading states
 * - AC 10: Error handling
 * - Auto-polling for real-time updates (30s interval)
 *
 * Note: Approve/Reject actions will be activated in Story 2.11.
 */
export function AdminRequestDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  // Fetch request detail with polling enabled
  const { data, isLoading, isError, error, refetch } = useAdminRequestDetail(id, {
    polling: true,
    pollInterval: 30000, // 30 seconds per FR44/NFR-PERF-8
  })

  const handleBack = () => {
    navigate('/admin/requests')
  }

  // Loading state
  if (isLoading) {
    return (
      <div className="space-y-6" data-testid="admin-request-detail-loading">
        <BackButton onClick={handleBack} />
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
        </div>
      </div>
    )
  }

  // Error state - Not Found (also handles Forbidden per security pattern)
  if (isError && error?.status === 404) {
    return (
      <div className="space-y-6" data-testid="admin-request-detail-not-found">
        <BackButton onClick={handleBack} />
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <FileText className="h-12 w-12 text-muted-foreground mb-4" />
          <h2 className="text-lg font-semibold mb-2">Request Not Found</h2>
          <p className="text-muted-foreground mb-4">
            The request you're looking for doesn't exist or you don't have access to it.
          </p>
          <Button variant="outline" asChild>
            <Link to="/admin/requests">View Pending Requests</Link>
          </Button>
        </div>
      </div>
    )
  }

  // General error state
  if (isError) {
    return (
      <div className="space-y-6" data-testid="admin-request-detail-error">
        <BackButton onClick={handleBack} />
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <AlertCircle className="h-12 w-12 text-destructive mb-4" />
          <h2 className="text-lg font-semibold mb-2">Error Loading Request</h2>
          <p className="text-muted-foreground mb-4">
            {error?.message || 'Could not load request details.'}
          </p>
          <Button variant="outline" onClick={() => refetch()}>
            Try Again
          </Button>
        </div>
      </div>
    )
  }

  // TypeScript type guard: After isLoading and isError checks, TanStack Query
  // guarantees data is defined. This check satisfies TypeScript's type narrowing
  // and provides defense-in-depth for unexpected edge cases.
  if (!data) {
    return null
  }

  return (
    <div className="space-y-6" data-testid="admin-request-detail-page">
      {/* Header with back button */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleBack}
            aria-label="Back to Pending Requests"
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold" data-testid="admin-request-detail-vm-name">
                {data.vmName}
              </h1>
              <StatusBadge status={data.status} />
            </div>
            <p className="text-muted-foreground" data-testid="admin-request-detail-project">
              Project: {data.projectName}
            </p>
          </div>
        </div>

        {/* Approve/Reject buttons placeholder for Story 2.11 */}
        {data.status === 'PENDING' && (
          <div className="flex gap-2">
            <Button
              variant="outline"
              disabled
              data-testid="reject-button"
              title="Available in Story 2.11"
            >
              Reject
            </Button>
            <Button disabled data-testid="approve-button" title="Available in Story 2.11">
              Approve
            </Button>
          </div>
        )}
      </div>

      {/* Main content grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left column - Request Details + Timeline */}
        <div className="lg:col-span-2 space-y-6">
          {/* Request Details Card (AC 3) */}
          <Card>
            <CardHeader>
              <CardTitle>Request Details</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Size specifications */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-muted">
                    <Cpu className="h-5 w-5 text-muted-foreground" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">CPU</p>
                    <p className="font-medium" data-testid="admin-request-detail-cpu">
                      {data.cpuCores} cores
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-muted">
                    <MemoryStick className="h-5 w-5 text-muted-foreground" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Memory</p>
                    <p className="font-medium" data-testid="admin-request-detail-memory">
                      {data.memoryGb} GB
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-muted">
                    <HardDrive className="h-5 w-5 text-muted-foreground" />
                  </div>
                  <div>
                    <p className="text-sm text-muted-foreground">Disk</p>
                    <p className="font-medium" data-testid="admin-request-detail-disk">
                      {data.diskGb} GB
                    </p>
                  </div>
                </div>
              </div>

              {/* Justification */}
              <div>
                <p className="text-sm text-muted-foreground mb-2">Business Justification</p>
                <p
                  className="text-sm bg-muted p-3 rounded-lg"
                  data-testid="admin-request-detail-justification"
                >
                  {data.justification}
                </p>
              </div>

              {/* Metadata */}
              <div className="text-sm text-muted-foreground">
                <span data-testid="admin-request-detail-created">
                  Created: {formatDateTime(data.createdAt)}
                </span>
              </div>
            </CardContent>
          </Card>

          {/* Timeline Card (AC 5) */}
          <Card>
            <CardHeader>
              <CardTitle>Timeline</CardTitle>
            </CardHeader>
            <CardContent>
              <Timeline events={data.timeline} />
            </CardContent>
          </Card>
        </div>

        {/* Right column - Requester Info + History */}
        <div className="space-y-6">
          {/* Requester Information Card (AC 2) */}
          <Card>
            <CardHeader>
              <CardTitle>Requester Information</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-muted">
                  <User className="h-5 w-5 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Name</p>
                  <p className="font-medium" data-testid="admin-request-detail-requester-name">
                    {data.requester.name}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-muted">
                  <Mail className="h-5 w-5 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Email</p>
                  <a
                    href={`mailto:${data.requester.email}`}
                    className="font-medium text-primary hover:underline"
                    data-testid="admin-request-detail-requester-email"
                  >
                    {data.requester.email}
                  </a>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-muted">
                  <Briefcase className="h-5 w-5 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Role</p>
                  <p className="font-medium" data-testid="admin-request-detail-requester-role">
                    {data.requester.role}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Project Context Card (AC 4) - Placeholder for Epic 4 */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <FolderKanban className="h-5 w-5" />
                Project Context
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p
                className="text-sm text-muted-foreground text-center py-4"
                data-testid="admin-request-detail-quota-placeholder"
              >
                Quota information available in Epic 4
              </p>
            </CardContent>
          </Card>

          {/* Requester History Card (AC 6) */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <History className="h-5 w-5" />
                Recent Requests
              </CardTitle>
            </CardHeader>
            <CardContent>
              {data.requesterHistory.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">
                  No previous requests from this user.
                </p>
              ) : (
                <div className="space-y-3" data-testid="admin-request-detail-history">
                  {data.requesterHistory.map((request) => (
                    <div
                      key={request.id}
                      className="flex items-center justify-between p-3 rounded-lg border"
                    >
                      <div className="flex-1 min-w-0">
                        <Link
                          to={`/admin/requests/${request.id}`}
                          className="font-medium text-sm hover:underline truncate block"
                          data-testid={`history-item-${request.id}`}
                        >
                          {request.vmName}
                        </Link>
                        <p className="text-xs text-muted-foreground">
                          {formatRelativeTime(request.createdAt)}
                        </p>
                      </div>
                      <StatusBadge status={request.status} />
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

interface BackButtonProps {
  onClick: () => void
}

function BackButton({ onClick }: BackButtonProps) {
  return (
    <Button variant="ghost" size="sm" className="gap-2" onClick={onClick}>
      <ArrowLeft className="h-4 w-4" />
      Back to Pending Requests
    </Button>
  )
}
