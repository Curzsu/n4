import type { AlarmRecord } from '@/api/types'

/**
 * Normalize a raw alarm payload into a consistent AlarmRecord.
 *
 * Rules:
 * - id / source: use alarm.source
 * - time: format alarm.timestamp, fallback to alarm.time
 * - severity: use alarm.severity
 * - message: use alarm.message
 * - location: use alarm.location
 * - status: use alarm.status, default Unack
 * - ackBy: use alarm.ackBy, default -
 */
export function normalizeRecord(alarm: any): AlarmRecord {
  const formatTime = (ts: number) => {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }

  return {
    id: alarm.source,
    time: alarm.timestamp ? formatTime(alarm.timestamp) : (alarm.time || '-'),
    value: alarm.value || alarm.pointValue || '',
    severity: alarm.severity || '',
    source: alarm.source || '',
    message: alarm.message || '',
    location: alarm.location || '',
    status: alarm.status || 'Unack',
    ackBy: alarm.ackBy || '-',
    duration: alarm.duration || '0',
    stage: alarm.stage || '',
    stageMessage: alarm.stageMessage || '',
    // Backend Ack matching uses mqttPath; WS payloads carry it, API DTOs use path=mqttPath
    path: alarm.mqttPath || alarm.path || '',
  }
}

/**
 * Map alarm severity to Element Plus tag type.
 */
export function severityTagType(severity: string): string {
  switch (severity) {
    case 'Critical': return 'danger'
    case 'Major': return 'warning'
    case 'Minor': return 'info'
    case 'Warning': return 'primary'
    default: return 'info'
  }
}
