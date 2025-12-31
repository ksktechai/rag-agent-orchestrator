import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Cleanup after each test
afterEach(() => {
    cleanup()
})

// Mock EventSource globally
class MockEventSource {
    url: string
    readyState: number = 0
    onopen: ((event: Event) => void) | null = null
    onmessage: ((event: MessageEvent) => void) | null = null
    onerror: ((event: Event) => void) | null = null

    private listeners: Map<string, ((event: MessageEvent) => void)[]> = new Map()

    constructor(url: string) {
        this.url = url
        this.readyState = 1 // OPEN
    }

    addEventListener(type: string, listener: (event: MessageEvent) => void): void {
        if (!this.listeners.has(type)) {
            this.listeners.set(type, [])
        }
        this.listeners.get(type)!.push(listener)
    }

    removeEventListener(type: string, listener: (event: MessageEvent) => void): void {
        const listeners = this.listeners.get(type)
        if (listeners) {
            const index = listeners.indexOf(listener)
            if (index !== -1) {
                listeners.splice(index, 1)
            }
        }
    }

    dispatchEvent(event: MessageEvent): boolean {
        const listeners = this.listeners.get(event.type)
        if (listeners) {
            listeners.forEach(listener => listener(event))
        }
        return true
    }

    close(): void {
        this.readyState = 2 // CLOSED
        this.listeners.clear()
    }

    // Test helper to trigger events
    _emit(type: string, data: string): void {
        const event = new MessageEvent(type, { data })
        this.dispatchEvent(event)
    }

    // Test helper to trigger error
    _triggerError(): void {
        if (this.onerror) {
            this.onerror(new Event('error'))
        }
    }
}

// Ensure global is properly typed
declare global {
    var EventSource: typeof MockEventSource
    var fetch: typeof globalThis.fetch
}

// Mock EventSource globally
vi.stubGlobal('EventSource', MockEventSource)

// Mock fetch globally
vi.stubGlobal('fetch', vi.fn())
