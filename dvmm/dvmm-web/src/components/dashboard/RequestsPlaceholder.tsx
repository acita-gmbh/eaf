import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { FileText } from 'lucide-react'

export function RequestsPlaceholder() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FileText className="h-5 w-5" />
          My Requests
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <div className="p-4 rounded-full bg-muted mb-4">
            <FileText className="h-8 w-8 text-muted-foreground" />
          </div>
          <p className="text-muted-foreground">
            Your VM requests will appear here
          </p>
        </div>
      </CardContent>
    </Card>
  )
}
