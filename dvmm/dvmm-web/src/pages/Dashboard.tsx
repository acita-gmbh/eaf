import { Button } from '@/components/ui/button'
import { StatsCard, RequestsPlaceholder } from '@/components/dashboard'
import { Plus, Clock, CheckCircle, Server } from 'lucide-react'

export function Dashboard() {
  return (
    <div className="space-y-6">
      {/* Page header with CTA */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground">
            Manage your virtual machine requests
          </p>
        </div>
        <Button
          size="lg"
          className="gap-2"
          // Note: Navigation will be functional when React Router is added in Story 2.4
        >
          <Plus className="h-5 w-5" />
          Request New VM
        </Button>
      </div>

      {/* Stats grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatsCard
          title="Pending Requests"
          value={0}
          icon={<Clock className="h-5 w-5" />}
          variant="pending"
        />
        <StatsCard
          title="Approved Requests"
          value={0}
          icon={<CheckCircle className="h-5 w-5" />}
          variant="approved"
        />
        <StatsCard
          title="Provisioned VMs"
          value={0}
          icon={<Server className="h-5 w-5" />}
          variant="info"
        />
      </div>

      {/* My Requests section */}
      <RequestsPlaceholder />
    </div>
  )
}
