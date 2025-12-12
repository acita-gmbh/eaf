import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useFormPersistence } from './useFormPersistence'

describe('useFormPersistence', () => {
  let addEventListenerSpy: ReturnType<typeof vi.spyOn>
  let removeEventListenerSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    addEventListenerSpy = vi.spyOn(window, 'addEventListener')
    removeEventListenerSpy = vi.spyOn(window, 'removeEventListener')
  })

  afterEach(() => {
    addEventListenerSpy.mockRestore()
    removeEventListenerSpy.mockRestore()
  })

  it('adds beforeunload event listener on mount', () => {
    renderHook(() => useFormPersistence(true))

    expect(addEventListenerSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
  })

  it('removes beforeunload event listener on unmount', () => {
    const { unmount } = renderHook(() => useFormPersistence(true))

    unmount()

    expect(removeEventListenerSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
  })

  it('re-registers listener when isDirty changes', () => {
    const { rerender } = renderHook(
      ({ isDirty }) => useFormPersistence(isDirty),
      { initialProps: { isDirty: false } }
    )

    // Initial registration
    expect(addEventListenerSpy).toHaveBeenCalledTimes(1)

    // Change isDirty
    rerender({ isDirty: true })

    // Should have removed old listener and added new one
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1)
    expect(addEventListenerSpy).toHaveBeenCalledTimes(2)
  })

  it('calls preventDefault when isDirty is true', () => {
    renderHook(() => useFormPersistence(true))

    const handler = addEventListenerSpy.mock.calls.find(
      (call: [string, EventListener]) => call[0] === 'beforeunload'
    )?.[1] as EventListener

    expect(handler).toBeDefined()

    const mockEvent = {
      preventDefault: vi.fn(),
      returnValue: '',
    } as unknown as BeforeUnloadEvent

    handler(mockEvent)

    expect(mockEvent.preventDefault).toHaveBeenCalled()
    expect(mockEvent.returnValue).toBe('Changes will not be saved. Continue?')
  })

  it('does not call preventDefault when isDirty is false', () => {
    renderHook(() => useFormPersistence(false))

    const handler = addEventListenerSpy.mock.calls.find(
      (call: [string, EventListener]) => call[0] === 'beforeunload'
    )?.[1] as EventListener

    expect(handler).toBeDefined()

    const mockEvent = {
      preventDefault: vi.fn(),
      returnValue: '',
    } as unknown as BeforeUnloadEvent

    handler(mockEvent)

    expect(mockEvent.preventDefault).not.toHaveBeenCalled()
    expect(mockEvent.returnValue).toBe('')
  })
})
