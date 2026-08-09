/**
 * 报警等级颜色配置
 *
 * 统一定义各报警等级的颜色，供报警弹窗（border-left）和报警列表（el-tag）
 * 共同使用，确保两者颜色一致。
 *
 * 修改此处的颜色值即可全局生效，无需改动各组件。
 *
 * color   — 主色（用于弹窗边框、标签边框+文字）
 * bgColor — 标签正常状态下的背景色（effect=dark）
 */

export interface SeverityColorConfig {
  color: string
  bgColor: string
}

export const severityColors: Record<string, SeverityColorConfig> = {
  Critical: { color: '#ef4444', bgColor: 'rgba(239, 68, 68, 0.15)' },
  Major:    { color: '#f97316', bgColor: 'rgba(249, 115, 22, 0.15)' },
  Minor:    { color: '#eab308', bgColor: 'rgba(234, 179, 8, 0.15)' },
  Warning:  { color: '#3b82f6', bgColor: 'rgba(59, 130, 246, 0.15)' },
}

/** 获取某等级的主色，未知等级返回灰色 */
export function getSeverityColor(severity: string): string {
  return severityColors[severity]?.color ?? '#6b7280'
}

/** 获取某等级的背景色，未知等级返回灰色半透明 */
export function getSeverityBgColor(severity: string): string {
  return severityColors[severity]?.bgColor ?? 'rgba(107, 114, 128, 0.15)'
}
