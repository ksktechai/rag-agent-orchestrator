import React, {useRef, useState} from 'react'

type AgentEvent = { agent: string; type: string; ts: string; data: Record<string, any> }
type Citation = { chunkId: number; title: string; chunkIndex: number; score: number }
type FinalAnswer = { answer: string; citations: Citation[] }
type IngestJobStartResponse = { jobId: string }
type IngestProgressEvent = {
    jobId: string
    stage: string
    current: number
    total: number
    message: string
    documentId?: number
    logicalId?: string
    version?: number
    done: boolean
    error?: string
    ts: string
}

export function App() {
    const [question, setQuestion] = useState('Summarize key points from ingested documents with evidence.')
    const [events, setEvents] = useState<AgentEvent[]>([])
    const [finalAnswer, setFinalAnswer] = useState<FinalAnswer | null>(null)
    const [streaming, setStreaming] = useState(false)
    const esRef = useRef<EventSource | null>(null)

    const [file, setFile] = useState<File | null>(null)
    const [ingestJobId, setIngestJobId] = useState<string | null>(null)
    const [ingestEvents, setIngestEvents] = useState<IngestProgressEvent[]>([])
    const ingestEsRef = useRef<EventSource | null>(null)

    function stopChat() {
        esRef.current?.close()
        esRef.current = null
        setStreaming(false)
    }

    function startChat() {
        stopChat()
        setEvents([])
        setFinalAnswer(null)
        setStreaming(true)

        const es = new EventSource(`/api/chat/stream?question=${encodeURIComponent(question)}&jobId=${encodeURIComponent(ingestJobId ?? "")}`)
        esRef.current = es

        es.addEventListener('agent', (msg) => {
            try {
                const ev = JSON.parse((msg as MessageEvent).data) as AgentEvent
                setEvents((p) => [...p, ev])
            } catch {
            }
        })

        es.addEventListener('final', (msg) => {
            try {
                const ans = JSON.parse((msg as MessageEvent).data) as FinalAnswer
                setFinalAnswer(ans)
            } finally {
                stopChat()
            }
        })

        es.onerror = () => stopChat()
    }

    function stopIngest() {
        ingestEsRef.current?.close()
        ingestEsRef.current = null
    }

    function startIngestProgress(jobId: string) {
        stopIngest()
        setIngestEvents([])
        setIngestJobId(jobId)

        const es = new EventSource(`/api/ingest/progress?jobId=${encodeURIComponent(jobId)}`)
        ingestEsRef.current = es

        es.addEventListener('progress', (msg) => {
            try {
                const ev = JSON.parse((msg as MessageEvent).data) as IngestProgressEvent
                setIngestEvents((p) => [...p, ev])
                if (ev.done) stopIngest()
            } catch {
            }
        })

        es.onerror = () => stopIngest()
    }

    async function uploadFile() {
        if (!file) return
        const fd = new FormData()
        fd.append('file', file)

        const res = await fetch('/api/ingest/file', {method: 'POST', body: fd})
        if (!res.ok) {
            const t = await res.text()
            alert(`Upload failed: ${t}`)
            return
        }
        const body = (await res.json()) as IngestJobStartResponse
        startIngestProgress(body.jobId)
    }

    return (
        <div className="min-h-screen">
            <header className="border-b bg-white">
                <div className="mx-auto max-w-6xl px-6 py-5">
                    <h1 className="text-2xl font-semibold">Agentic RAG (SSE • Local)</h1>
                    <p className="text-sm text-slate-600 mt-1">Spring Boot 4 + Java 25 + Ollama + pgvector +
                        React/Tailwind</p>
                </div>
            </header>

            <main className="mx-auto max-w-6xl px-6 py-6 space-y-6">
                <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <h2 className="font-semibold">Ingest a document (multipart + progress SSE)</h2>
                        {ingestJobId ? <span className="text-xs text-slate-500">job: {ingestJobId}</span> : null}
                    </div>

                    <div className="mt-3 flex flex-col md:flex-row gap-3 md:items-center">
                        <input type="file" className="block w-full text-sm"
                               onChange={(e) => setFile(e.target.files?.[0] ?? null)}/>
                        <button
                            onClick={uploadFile}
                            className="rounded-xl bg-slate-900 text-white px-4 py-3 text-sm shadow-sm disabled:opacity-50"
                            disabled={!file}
                        >
                            Upload & Ingest
                        </button>
                    </div>

                    {ingestEvents.length === 0 ? (
                        <div className="mt-3 text-sm text-slate-600">Upload a file to start ingestion. Progress will
                            stream here.</div>
                    ) : (
                        <div className="mt-3 space-y-2">
                            {ingestEvents.slice(-8).map((e, i) => (
                                <div key={i} className="rounded-xl border border-slate-200 p-3 text-sm">
                                    <div className="flex justify-between">
                                        <div>
                                            <span className="font-medium">{e.stage}</span>
                                            <span className="text-slate-500"> • {e.message}</span>
                                        </div>
                                        <div
                                            className="text-xs text-slate-400">{new Date(e.ts).toLocaleTimeString()}</div>
                                    </div>
                                    {e.done && e.documentId ? (
                                        <div className="mt-1 text-xs text-slate-600">
                                            ✅ documentId={e.documentId} • logicalId={e.logicalId} • version={e.version}
                                        </div>
                                    ) : null}
                                    {e.error ? <div className="mt-1 text-xs text-red-600">Error: {e.error}</div> : null}
                                </div>
                            ))}
                        </div>
                    )}
                </section>

                <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex gap-3 items-center">
                        <input
                            className="flex-1 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-slate-300"
                            value={question}
                            onChange={(e) => setQuestion(e.target.value)}
                        />
                        {streaming ? (
                            <button onClick={stopChat}
                                    className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm shadow-sm">
                                Stop
                            </button>
                        ) : (
                            <button onClick={startChat}
                                    className="rounded-xl bg-slate-900 text-white px-4 py-3 text-sm shadow-sm">
                                Ask (SSE)
                            </button>
                        )}
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 mt-6">
                        <section className="lg:col-span-2 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                            <div className="flex items-center justify-between">
                                <h2 className="font-semibold">Answer</h2>
                                <span className="text-xs text-slate-500">{streaming ? 'streaming…' : 'idle'}</span>
                            </div>

                            {!finalAnswer ? (
                                <div
                                    className="mt-4 text-sm text-slate-600">{streaming ? 'Waiting for final answer…' : 'Click Ask to start.'}</div>
                            ) : (
                                <pre
                                    className="mt-4 whitespace-pre-wrap text-sm text-slate-900">{finalAnswer.answer}</pre>
                            )}
                        </section>

                        <aside className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                            <h2 className="font-semibold">Citations</h2>
                            {!finalAnswer || finalAnswer.citations.length === 0 ? (
                                <div className="mt-3 text-sm text-slate-600">No citations yet (ingest docs first).</div>
                            ) : (
                                <ol className="mt-3 space-y-3 list-decimal list-inside text-sm">
                                    {finalAnswer.citations.map((c) => (
                                        <li key={c.chunkId}>
                                            <div className="font-medium">{c.title}</div>
                                            <div className="text-xs text-slate-600">
                                                chunk #{c.chunkIndex} • id {c.chunkId} • score {c.score.toFixed(3)}
                                            </div>
                                        </li>
                                    ))}
                                </ol>
                            )}
                        </aside>
                    </div>

                    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                        <h2 className="font-semibold">Agent Trace</h2>
                        {events.length === 0 ? (
                            <div className="mt-3 text-sm text-slate-600">No events yet.</div>
                        ) : (
                            <div className="mt-3 space-y-3">
                                {events.map((e, idx) => (
                                    <details key={idx} className="rounded-xl border border-slate-200 p-3"
                                             open={idx < 2}>
                                        <summary className="cursor-pointer text-sm">
                                            <span className="font-medium">{e.agent}</span>
                                            <span className="text-slate-500"> • {e.type}</span>
                                            <span
                                                className="text-slate-400"> • {new Date(e.ts).toLocaleTimeString()}</span>
                                        </summary>
                                        <pre
                                            className="mt-2 whitespace-pre-wrap text-xs text-slate-700">{JSON.stringify(e.data, null, 2)}</pre>
                                    </details>
                                ))}
                            </div>
                        )}
                    </section>
                </section>
            </main>
        </div>
    )
}
