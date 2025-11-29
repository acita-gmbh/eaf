import { useAuth } from 'react-oidc-context'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { getTenantIdFromToken, getUserNameFromToken } from '@/auth/auth-config'
import { LogOut, Building2, Menu } from 'lucide-react'

interface HeaderProps {
  onMobileMenuToggle?: () => void
}

export function Header({ onMobileMenuToggle }: HeaderProps) {
  const auth = useAuth()

  const userName = getUserNameFromToken(auth.user?.access_token) ?? 'User'
  const tenantId = getTenantIdFromToken(auth.user?.access_token)

  // Get initials for avatar (handles empty strings and multiple spaces)
  const getInitials = (name: string): string => {
    return name
      .split(' ')
      .filter(part => part.length > 0)
      .map(part => part[0])
      .join('')
      .toUpperCase()
      .slice(0, 2)
  }

  return (
    <header className="h-14 border-b bg-card flex items-center px-4 gap-4 sticky top-0 z-40">
      {/* Mobile menu button */}
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        onClick={onMobileMenuToggle}
        aria-label="Toggle navigation menu"
      >
        <Menu className="h-5 w-5" />
      </Button>

      {/* Logo */}
      <div className="flex items-center gap-2">
        <h1 className="text-xl font-bold text-primary">DVMM</h1>
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Tenant info */}
      {tenantId && (
        <div className="hidden sm:flex items-center gap-2 text-sm text-muted-foreground">
          <Building2 className="h-4 w-4" />
          <span>{tenantId}</span>
        </div>
      )}

      {/* User info */}
      <div className="flex items-center gap-2">
        <Avatar className="h-8 w-8">
          <AvatarFallback className="bg-primary text-primary-foreground text-xs">
            {getInitials(userName)}
          </AvatarFallback>
        </Avatar>
        <span className="hidden sm:block text-sm">{userName}</span>
      </div>

      {/* Logout button */}
      <Button
        variant="ghost"
        size="sm"
        onClick={() => auth.signoutRedirect()}
        className="gap-2"
        aria-label="Sign out"
      >
        <LogOut className="h-4 w-4" />
        <span className="hidden sm:inline">Sign out</span>
      </Button>
    </header>
  )
}
