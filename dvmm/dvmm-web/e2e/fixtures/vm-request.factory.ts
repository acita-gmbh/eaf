
import { test as base } from '@playwright/test';

// Define the interface for the create request payload
interface CreateVmRequest {
    vmName: string;
    projectId: string;
    size: 'S' | 'M' | 'L' | 'XL';
    justification: string;
}

interface VmRequestResponse {
    requestId: string;
    vmName: string;
    projectId: string;
    size: 'S' | 'M' | 'L' | 'XL';
    status: string;
    createdAt: string;
}

// Define the type for the factory function
export type VmRequestFactory = (overrides?: Partial<CreateVmRequest>) => Promise<VmRequestResponse>;

// Extend the test fixture
export const test = base.extend<{ requestFactory: VmRequestFactory }>({
    requestFactory: async ({ request }, run) => {
        // Factory implementation
        const createRequest = async (overrides: Partial<CreateVmRequest> = {}) => {
            const defaultRequest: CreateVmRequest = {
                vmName: `test-vm-${Date.now()}`,
                projectId: '550e8400-e29b-41d4-a716-446655440000', // Default generic UUID or fixture constant
                size: 'S',
                justification: 'Automated test request'
            };

            const payload = { ...defaultRequest, ...overrides };

            const response = await request.post('/api/requests', {
                data: payload
            });

            if (!response.ok()) {
                throw new Error(`Failed to create VM request: ${response.status()} ${await response.text()}`);
            }

            return await response.json();
        };

        await run(createRequest);
    }
});

export { expect } from '@playwright/test';
