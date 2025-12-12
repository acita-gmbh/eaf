import { Link, useLocation } from 'react-router-dom'
import { LayoutDashboard, FileText, Plus, Shield, Settings } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { useIsAdmin } from '@/hooks/useIsAdmin'

interface NavItem {
  label: string
  icon: React.ReactNode
  href: string
  badge?: number
  adminOnly?: boolean
}

const navItems: NavItem[] = [
  {
    label: 'Dashboard',
    icon: <LayoutDashboard className="h-5 w-5" />,
    href: '/',
  },
  {
    label: 'My Requests',
    icon: <FileText className="h-5 w-5" />,
    href: '/requests',
  },
  {
    label: 'Request New VM',
    icon: <Plus className="h-5 w-5" />,
    href: '/requests/new',
  },
  {
    label: 'Admin Queue',
    icon: <Shield className="h-5 w-5" />,
    href: '/admin/requests',
    adminOnly: true,
  },
  {
    label: 'Settings',
    icon: <Settings className="h-5 w-5" />,
    href: '/admin/settings',
    adminOnly: true,
  },
]

interface SidebarProps {
  className?: string
}

export function Sidebar({ className }: Readonly<SidebarProps>) {
  const location = useLocation()
  const currentPath = location.pathname
  const isAdmin = useIsAdmin()

  // Filter out admin-only items for non-admin users
  const visibleItems = navItems.filter((item) => !item.adminOnly || isAdmin)

  return (
    <aside className={cn('w-56 border-r bg-card', className)}>
      <nav
        className="p-4 space-y-2"
        role="navigation"
        aria-label="Main navigation"
        data-onboarding="sidebar-nav"
      >
        {visibleItems.map((item) => {
          const isActive = currentPath === item.href

          return (
            <Link
              key={item.href}
              to={item.href}
              className={cn(
                'w-full flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                'hover:bg-accent hover:text-accent-foreground',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                isActive && 'border-l-2 border-primary bg-primary/10 text-primary rounded-l-none'
              )}
              aria-current={isActive ? 'page' : undefined}
            >
              {item.icon}
              <span className="flex-1 text-left">{item.label}</span>
              {item.badge !== undefined && item.badge > 0 && (
                <Badge variant="secondary" className="ml-auto">
                  {item.badge}
                </Badge>
              )}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
