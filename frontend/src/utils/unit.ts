/**
 * Normalizes engineering units, repairing known character encoding corruptions
 * such as degree Celsius (\u00B0C) being corrupted into \u64B8C (掳C in GBK), ?C, or \uFFFDC.
 */
export function normalizeUnit(value?: string | null): string {
  if (!value) return ''
  const trimmed = value.trim()
  if (!trimmed) return ''

  const malformedDegree =
    trimmed.includes('?') ||
    trimmed.includes('\uFFFD') ||
    trimmed.includes('\u00B0') ||
    trimmed.includes('\u64B8') ||
    trimmed.includes('\u63B3')

  if (malformedDegree && (trimmed.endsWith('C') || trimmed.endsWith('c'))) {
    return '\u00B0C'
  }

  return trimmed
}
