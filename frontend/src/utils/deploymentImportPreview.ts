import type { DeploymentPreviewItem } from '@/api/types'

export interface DeploymentPreviewTreeRow extends DeploymentPreviewItem {
  id: string
  rowType: 'system' | 'device'
  deviceCount?: number
  children?: DeploymentPreviewTreeRow[]
}

export function buildDeploymentPreviewTree(
  items: DeploymentPreviewItem[] = [],
): DeploymentPreviewTreeRow[] {
  const groups = new Map<string, DeploymentPreviewTreeRow>()

  items.forEach((item, index) => {
    const groupId = 'system-' + (item.systemName || 'unknown')
    let group = groups.get(groupId)
    if (!group) {
      group = {
        id: groupId,
        rowType: 'system',
        sourceFile: '',
        deviceName: '',
        devicePath: '',
        systemName: item.systemName,
        templateName: '',
        pointCount: 0,
        action: 'GROUP',
        deviceCount: 0,
        children: [],
      }
      groups.set(groupId, group)
    }

    group.children?.push({
      ...item,
      id: groupId + '-device-' + index,
      rowType: 'device',
    })
    group.deviceCount = (group.deviceCount ?? 0) + 1
    group.pointCount += item.pointCount
  })

  return Array.from(groups.values())
}
