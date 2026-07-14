/**
 * Format elapsed seconds into a human-readable duration string.
 *
 * Rules:
 * - < 60s           -> "31s"
 * - < 1h            -> "1m31s"
 * - < 24h           -> "3h20m31s"
 * - >= 24h          -> "1d3h20m31s"
 */
export function formatDuration(totalSeconds: number): string {
  if (totalSeconds < 0) totalSeconds = 0

  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = Math.floor(totalSeconds % 60)

  if (days > 0) return `${days}d${hours}h${minutes}m${seconds}s`
  if (hours > 0) return `${hours}h${minutes}m${seconds}s`
  if (minutes > 0) return `${minutes}m${seconds}s`
  return `${seconds}s`
}

/**
 * Parse alarm time string (yyyy-MM-dd HH:mm:ss) into a Date object.
 */
export function parseAlarmTime(timeStr: string): Date | null {
  if (!timeStr || timeStr === '-') return null
  try {
    const d = new Date(timeStr.replace(' ', 'T'))
    return isNaN(d.getTime()) ? null : d
  } catch {
    return null
  }
}
