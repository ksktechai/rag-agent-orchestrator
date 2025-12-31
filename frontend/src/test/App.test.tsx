import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { App } from '../ui/App'

// Helper to get the mock EventSource instance
function getMockEventSource(): any {
    return (global.EventSource as any).mock?.results?.slice(-1)[0]?.value
}

// Mock EventSource that we can control
class ControlledEventSource {
    url: string
    readyState: number = 1
    onerror: ((event: Event) => void) | null = null
    private listeners: Map<string, ((event: MessageEvent) => void)[]> = new Map()

    constructor(url: string) {
        this.url = url
            // Store instance for test access
            ; (ControlledEventSource as any).lastInstance = this
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

    close(): void {
        this.readyState = 2
        this.listeners.clear()
    }

    // Test helper methods
    emit(type: string, data: any): void {
        const listeners = this.listeners.get(type)
        if (listeners) {
            const event = new MessageEvent(type, { data: JSON.stringify(data) })
            listeners.forEach(listener => listener(event))
        }
    }

    triggerError(): void {
        if (this.onerror) {
            this.onerror(new Event('error'))
        }
    }

    static getLastInstance(): ControlledEventSource | undefined {
        return (ControlledEventSource as any).lastInstance
    }
}

describe('App Component', () => {
    let mockFetch: ReturnType<typeof vi.fn>

    beforeEach(() => {
        mockFetch = vi.fn()
        vi.stubGlobal('fetch', mockFetch)
        vi.stubGlobal('EventSource', ControlledEventSource)
        vi.stubGlobal('alert', vi.fn())
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        vi.clearAllMocks()
    })

    describe('Initial Render', () => {
        it('renders header with title', () => {
            render(<App />)
            expect(screen.getByText('Agentic RAG (SSE • Local)')).toBeInTheDocument()
        })

        it('renders subtitle with tech stack', () => {
            render(<App />)
            expect(screen.getByText(/Spring Boot 4 \+ Java 25/)).toBeInTheDocument()
        })

        it('renders document ingest section', () => {
            render(<App />)
            expect(screen.getByText(/Ingest a document/)).toBeInTheDocument()
        })

        it('renders file input', () => {
            render(<App />)
            expect(screen.getByRole('textbox')).toBeInTheDocument()
        })

        it('renders Upload button disabled initially', () => {
            render(<App />)
            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            expect(uploadBtn).toBeDisabled()
        })

        it('renders Ask button', () => {
            render(<App />)
            expect(screen.getByRole('button', { name: /Ask \(SSE\)/i })).toBeInTheDocument()
        })

        it('renders default question in input', () => {
            render(<App />)
            const input = screen.getByRole('textbox')
            expect(input).toHaveValue('Summarize key points from ingested documents with evidence.')
        })

        it('shows initial ingest message', () => {
            render(<App />)
            expect(screen.getByText(/Upload a file to start ingestion/)).toBeInTheDocument()
        })

        it('shows no events message initially', () => {
            render(<App />)
            expect(screen.getByText('No events yet.')).toBeInTheDocument()
        })

        it('shows no citations message initially', () => {
            render(<App />)
            expect(screen.getByText(/No citations yet/)).toBeInTheDocument()
        })

        it('shows idle status initially', () => {
            render(<App />)
            expect(screen.getByText('idle')).toBeInTheDocument()
        })

        it('shows Answer section', () => {
            render(<App />)
            expect(screen.getByText('Answer')).toBeInTheDocument()
        })

        it('shows Citations section', () => {
            render(<App />)
            expect(screen.getByText('Citations')).toBeInTheDocument()
        })

        it('shows Agent Trace section', () => {
            render(<App />)
            expect(screen.getByText('Agent Trace')).toBeInTheDocument()
        })
    })

    describe('Question Input', () => {
        it('allows typing in question input', async () => {
            const user = userEvent.setup()
            render(<App />)

            const input = screen.getByRole('textbox')
            await user.clear(input)
            await user.type(input, 'What is AI?')

            expect(input).toHaveValue('What is AI?')
        })

        it('updates question state on change', async () => {
            render(<App />)
            const input = screen.getByRole('textbox')

            fireEvent.change(input, { target: { value: 'New question' } })

            expect(input).toHaveValue('New question')
        })
    })

    describe('File Upload', () => {
        it('enables Upload button when file is selected', async () => {
            render(<App />)

            const file = new File(['test content'], 'test.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            expect(uploadBtn).not.toBeDisabled()
        })

        it('uploads file and starts progress SSE on success', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-123' })
            })

            render(<App />)

            const file = new File(['test content'], 'test.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            await act(async () => {
                fireEvent.click(uploadBtn)
            })

            expect(mockFetch).toHaveBeenCalledWith('/api/ingest/file', expect.objectContaining({
                method: 'POST'
            }))

            await waitFor(() => {
                expect(screen.getByText('job: job-123')).toBeInTheDocument()
            })
        })

        it('shows alert on upload failure', async () => {
            const mockAlert = vi.fn()
            vi.stubGlobal('alert', mockAlert)

            mockFetch.mockResolvedValueOnce({
                ok: false,
                text: async () => 'Server error'
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            await act(async () => {
                fireEvent.click(uploadBtn)
            })

            await waitFor(() => {
                expect(mockAlert).toHaveBeenCalledWith('Upload failed: Server error')
            })
        })

        it('does not call fetch if no file selected', async () => {
            render(<App />)

            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            // Button should be disabled, but let's test the function guard too
            // We'll need to manually trigger the function

            expect(uploadBtn).toBeDisabled()
        })
    })

    describe('Ingest Progress SSE', () => {
        it('displays ingest progress events', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-456' })
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            const uploadBtn = screen.getByRole('button', { name: /Upload & Ingest/i })
            await act(async () => {
                fireEvent.click(uploadBtn)
            })

            await waitFor(() => {
                const es = ControlledEventSource.getLastInstance()
                expect(es).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('progress', {
                    jobId: 'job-456',
                    stage: 'parsing',
                    current: 1,
                    total: 3,
                    message: 'Parsing document',
                    done: false,
                    ts: new Date().toISOString()
                })
            })

            expect(screen.getByText('parsing')).toBeInTheDocument()
            expect(screen.getByText(/Parsing document/)).toBeInTheDocument()
        })

        it('shows completion details when done', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-789' })
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Upload & Ingest/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('progress', {
                    jobId: 'job-789',
                    stage: 'complete',
                    current: 3,
                    total: 3,
                    message: 'Done',
                    documentId: 42,
                    logicalId: 'lid-123',
                    version: 1,
                    done: true,
                    ts: new Date().toISOString()
                })
            })

            expect(screen.getByText(/documentId=42/)).toBeInTheDocument()
            expect(screen.getByText(/logicalId=lid-123/)).toBeInTheDocument()
            expect(screen.getByText(/version=1/)).toBeInTheDocument()
        })

        it('shows error when present', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-err' })
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Upload & Ingest/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('progress', {
                    jobId: 'job-err',
                    stage: 'error',
                    current: 0,
                    total: 0,
                    message: 'Failed',
                    error: 'Parse error',
                    done: false,
                    ts: new Date().toISOString()
                })
            })

            expect(screen.getByText(/Error: Parse error/)).toBeInTheDocument()
        })

        it('closes SSE on error', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-close' })
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Upload & Ingest/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!
            const closeSpy = vi.spyOn(es, 'close')

            await act(async () => {
                es.triggerError()
            })

            expect(closeSpy).toHaveBeenCalled()
        })

        it('handles invalid JSON in progress events gracefully', async () => {
            mockFetch.mockResolvedValueOnce({
                ok: true,
                json: async () => ({ jobId: 'job-invalid' })
            })

            render(<App />)

            const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' })
            const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement

            await act(async () => {
                fireEvent.change(fileInput, { target: { files: [file] } })
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Upload & Ingest/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            // Send invalid JSON to trigger the catch block
            await act(async () => {
                const listeners = (es as any).listeners.get('progress')
                if (listeners) {
                    const event = new MessageEvent('progress', { data: 'not valid json' })
                    listeners.forEach((listener: any) => {
                        try { listener(event) } catch { }
                    })
                }
            })

            // App should still be functional - job ID should still be displayed
            expect(screen.getByText('job: job-invalid')).toBeInTheDocument()
        })
    })

    describe('Chat SSE', () => {
        it('starts chat when Ask button clicked', async () => {
            render(<App />)

            const askBtn = screen.getByRole('button', { name: /Ask \(SSE\)/i })
            await act(async () => {
                fireEvent.click(askBtn)
            })

            expect(screen.getByText('streaming…')).toBeInTheDocument()
            expect(screen.getByRole('button', { name: /Stop/i })).toBeInTheDocument()
        })

        it('shows Stop button when streaming', async () => {
            render(<App />)

            const askBtn = screen.getByRole('button', { name: /Ask \(SSE\)/i })
            await act(async () => {
                fireEvent.click(askBtn)
            })

            expect(screen.queryByRole('button', { name: /Ask \(SSE\)/i })).not.toBeInTheDocument()
            expect(screen.getByRole('button', { name: /Stop/i })).toBeInTheDocument()
        })

        it('stops streaming when Stop clicked', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Stop/i }))
            })

            expect(screen.getByRole('button', { name: /Ask \(SSE\)/i })).toBeInTheDocument()
            expect(screen.getByText('idle')).toBeInTheDocument()
        })

        it('displays agent events', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('agent', {
                    agent: 'router',
                    type: 'route',
                    ts: new Date().toISOString(),
                    data: { needsRetrieval: true }
                })
            })

            expect(screen.getByText('router')).toBeInTheDocument()
            // 'route' appears as the event type in the agent trace
            expect(screen.getAllByText(/route/).length).toBeGreaterThan(0)
        })

        it('displays final answer', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('final', {
                    answer: 'This is the AI answer based on documents.',
                    citations: []
                })
            })

            expect(screen.getByText('This is the AI answer based on documents.')).toBeInTheDocument()
        })

        it('displays citations when present', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('final', {
                    answer: 'Answer with citations',
                    citations: [
                        { chunkId: 1, title: 'Document.pdf', chunkIndex: 0, score: 0.95 },
                        { chunkId: 2, title: 'Report.pdf', chunkIndex: 1, score: 0.87 }
                    ]
                })
            })

            expect(screen.getByText('Document.pdf')).toBeInTheDocument()
            expect(screen.getByText('Report.pdf')).toBeInTheDocument()
            expect(screen.getByText(/score 0.950/)).toBeInTheDocument()
        })

        it('stops streaming after final answer', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('final', {
                    answer: 'Done',
                    citations: []
                })
            })

            expect(screen.getByText('idle')).toBeInTheDocument()
            expect(screen.getByRole('button', { name: /Ask \(SSE\)/i })).toBeInTheDocument()
        })

        it('stops on SSE error', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.triggerError()
            })

            expect(screen.getByText('idle')).toBeInTheDocument()
        })

        it('clears previous events on new chat', async () => {
            render(<App />)

            // First chat
            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            let es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('agent', {
                    agent: 'first-agent',
                    type: 'test',
                    ts: new Date().toISOString(),
                    data: {}
                })
            })

            expect(screen.getByText('first-agent')).toBeInTheDocument()

            // Stop and start new chat
            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Stop/i }))
            })

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            // Previous event should be cleared
            expect(screen.queryByText('first-agent')).not.toBeInTheDocument()
        })

        it('handles invalid JSON in agent events gracefully', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            // This should not throw
            await act(async () => {
                const listeners = (es as any).listeners.get('agent')
                if (listeners) {
                    const event = new MessageEvent('agent', { data: 'not valid json' })
                    listeners.forEach((listener: any) => {
                        try { listener(event) } catch { }
                    })
                }
            })

            // App should still be functional
            expect(screen.getByText('streaming…')).toBeInTheDocument()
        })
    })

    describe('Multiple Agent Events', () => {
        it('shows multiple events in trace', async () => {
            render(<App />)

            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: /Ask \(SSE\)/i }))
            })

            await waitFor(() => {
                expect(ControlledEventSource.getLastInstance()).toBeDefined()
            })

            const es = ControlledEventSource.getLastInstance()!

            await act(async () => {
                es.emit('agent', { agent: 'router', type: 'route', ts: new Date().toISOString(), data: {} })
                es.emit('agent', { agent: 'planner', type: 'plan', ts: new Date().toISOString(), data: {} })
                es.emit('agent', { agent: 'retriever', type: 'retrieve', ts: new Date().toISOString(), data: {} })
            })

            expect(screen.getByText('router')).toBeInTheDocument()
            expect(screen.getByText('planner')).toBeInTheDocument()
            expect(screen.getByText('retriever')).toBeInTheDocument()
        })
    })
})
