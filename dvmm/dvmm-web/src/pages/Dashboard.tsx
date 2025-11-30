import { useRef, useEffect, useState, Component, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { StatsCard, RequestsPlaceholder } from '@/components/dashboard'
import { OnboardingTooltip } from '@/components/onboarding'
import { useOnboarding } from '@/hooks/useOnboarding'
import { Plus, Clock, CheckCircle, Server } from 'lucide-react'

const ONBOARDING_CONTENT = {
  'cta-button': 'Hier starten Sie eine neue VM-Anfrage',
  'sidebar-nav': 'Navigieren Sie zu Ihren Anfragen',
} as const

// Error boundary to prevent onboarding issues from crashing the dashboard
class OnboardingErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean }
> {
  constructor(props: { children: ReactNode }) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('[OnboardingErrorBoundary] Caught error:', error, errorInfo)
  }

  render(): ReactNode {
    if (this.state.hasError) {
      // Silently fail - don't show onboarding if it errors
      return null
    }
    return this.props.children
  }
}

export function Dashboard() {
  const navigate = useNavigate()
  const ctaButtonRef = useRef<HTMLButtonElement>(null)
  const [ctaRect, setCtaRect] = useState<DOMRect | null>(null)
  const [sidebarRect, setSidebarRect] = useState<DOMRect | null>(null)
  const { currentStep, dismissStep, resetOnboarding } = useOnboarding()

  // Function to update anchor rectangles - React Compiler handles optimization
  const updateAnchorRects = () => {
    if (currentStep === 'cta-button') {
      const buttonElement = ctaButtonRef.current
      if (buttonElement) {
        try {
          setCtaRect(buttonElement.getBoundingClientRect())
        } catch (e) {
          console.error('[Dashboard] Failed to get CTA button rect:', e)
        }
      }
    } else if (currentStep === 'sidebar-nav') {
      const sidebarNav = document.querySelector('[data-onboarding="sidebar-nav"]')
      if (sidebarNav) {
        try {
          setSidebarRect(sidebarNav.getBoundingClientRect())
        } catch (e) {
          console.error('[Dashboard] Failed to get sidebar rect:', e)
        }
      } else {
        console.warn(
          '[Dashboard] Onboarding anchor not found: [data-onboarding="sidebar-nav"]',
          'Skipping sidebar tooltip step.'
        )
        // Skip to next step since anchor is missing
        dismissStep()
      }
    }
  }

  // Update anchor rectangles when step changes
  useEffect(() => {
    updateAnchorRects()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- updateAnchorRects depends on currentStep/dismissStep
  }, [currentStep])

  // Update anchor positions on window resize
  useEffect(() => {
    if (currentStep) {
      window.addEventListener('resize', updateAnchorRects)
      return () => window.removeEventListener('resize', updateAnchorRects)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- updateAnchorRects depends on currentStep/dismissStep
  }, [currentStep])

  // Keyboard shortcut for dev reset (Ctrl+Shift+O)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'O') {
        resetOnboarding()
        console.log('[Dev] Onboarding reset')
      }
    }

    if (import.meta.env.DEV) {
      window.addEventListener('keydown', handleKeyDown)
      return () => window.removeEventListener('keydown', handleKeyDown)
    }
  }, [resetOnboarding])

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
          onClick={() => navigate('/requests/new')}
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

      {/* Onboarding tooltips - wrapped in error boundary to prevent crashes */}
      <OnboardingErrorBoundary>
        {currentStep === 'cta-button' && ctaRect && (
          <OnboardingTooltip
            content={ONBOARDING_CONTENT['cta-button']}
            onDismiss={dismissStep}
            anchorRect={ctaRect}
            side="bottom"
          />
        )}
        {currentStep === 'sidebar-nav' && sidebarRect && (
          <OnboardingTooltip
            content={ONBOARDING_CONTENT['sidebar-nav']}
            onDismiss={dismissStep}
            anchorRect={sidebarRect}
            side="right"
          />
        )}
      </OnboardingErrorBoundary>
    </div>
  )
}
