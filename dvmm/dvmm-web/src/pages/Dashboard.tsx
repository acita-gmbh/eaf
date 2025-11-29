import { useRef, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { StatsCard, RequestsPlaceholder } from '@/components/dashboard'
import { OnboardingTooltip } from '@/components/onboarding'
import { useOnboarding } from '@/hooks/useOnboarding'
import { Plus, Clock, CheckCircle, Server } from 'lucide-react'

const ONBOARDING_CONTENT = {
  'cta-button': 'Hier starten Sie eine neue VM-Anfrage',
  'sidebar-nav': 'Navigieren Sie zu Ihren Anfragen',
} as const

export function Dashboard() {
  const ctaButtonRef = useRef<HTMLButtonElement>(null)
  const [ctaRect, setCtaRect] = useState<DOMRect | null>(null)
  const [sidebarRect, setSidebarRect] = useState<DOMRect | null>(null)
  const onboarding = useOnboarding()

  // Update anchor rectangles when step changes
  useEffect(() => {
    if (onboarding.currentStep === 'cta-button' && ctaButtonRef.current) {
      setCtaRect(ctaButtonRef.current.getBoundingClientRect())
    } else if (onboarding.currentStep === 'sidebar-nav') {
      const sidebarNav = document.querySelector('[data-onboarding="sidebar-nav"]')
      if (sidebarNav) {
        setSidebarRect(sidebarNav.getBoundingClientRect())
      }
    }
  }, [onboarding.currentStep])

  // Keyboard shortcut for dev reset (Ctrl+Shift+O)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'O') {
        onboarding.resetOnboarding()
        console.log('[Dev] Onboarding reset')
      }
    }

    if (import.meta.env.DEV) {
      window.addEventListener('keydown', handleKeyDown)
      return () => window.removeEventListener('keydown', handleKeyDown)
    }
  }, [onboarding])

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
          ref={ctaButtonRef}
          size="lg"
          className="gap-2"
          data-onboarding="cta-button"
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

      {/* Onboarding tooltips */}
      {onboarding.currentStep === 'cta-button' && ctaRect && (
        <OnboardingTooltip
          content={ONBOARDING_CONTENT['cta-button']}
          onDismiss={onboarding.dismissStep}
          anchorRect={ctaRect}
          side="bottom"
        />
      )}
      {onboarding.currentStep === 'sidebar-nav' && sidebarRect && (
        <OnboardingTooltip
          content={ONBOARDING_CONTENT['sidebar-nav']}
          onDismiss={onboarding.dismissStep}
          anchorRect={sidebarRect}
          side="right"
        />
      )}
    </div>
  )
}
