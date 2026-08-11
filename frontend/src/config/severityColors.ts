/**
 * 报警等级颜色配置
 *
 * 统一定义各报警等级的颜色，供报警弹窗（border-left）和报警列表（el-tag）
 * 共同使用，确保两者颜色一致。
 *
 * 修改此处的颜色值即可全局生效，无需改动各组件。
 *
 * color   — 主色（用于弹窗、标签背景色）
 */

export const severityColors: Record<string, string> = {
  Critical: '#FF0000',
  Major: '#FFC000',
  Minor: '#FFFF00',
  Warning: '#3b82f6',
}

/** 获取某等级的主色，未知等级返回灰色 */
export function getSeverityColor(severity: string): string {
  return severityColors[severity] ?? '#6b7280'
}

