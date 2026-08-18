/**
 * Alarm Severity Colors Configuration
 *
 * Uniformly defines colors for each alarm severity level, used by both
 * alarm popup dialogs (border-left) and alarm tables (el-tag).
 *
 * Changing color values here applies globally without modifying individual components.
 */

export const severityColors: Record<string, string> = {
  Critical: '#FF0000',
  Major: '#FFC000',
  Minor: '#FFFF00',
  Warning: '#3b82f6',
}

/** Get primary color for severity; returns gray for unknown levels */
export function getSeverityColor(severity: string): string {
  return severityColors[severity] ?? '#6b7280'
}

