import { useState } from 'react'
import { Header } from './Header'
import { Sidebar } from './Sidebar'
import { MobileNav } from './MobileNav'

interface DashboardLayoutProps {
  children: React.ReactNode
}

export function DashboardLayout({ children }: Readonly<DashboardLayoutProps>) {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <Header onMobileMenuToggle={() => setMobileNavOpen(true)} />

      <div className="flex">
        {/* Desktop sidebar - hidden on mobile */}
        <Sidebar className="hidden md:block h-[calc(100vh-3.5rem)] sticky top-14" />

        {/* Mobile navigation */}
        <MobileNav
          open={mobileNavOpen}
          onOpenChange={setMobileNavOpen}
        />

        {/* Main content */}
        <main className="flex-1 p-6">
          {children}
        </main>
      </div>
    </div>
  )
}
