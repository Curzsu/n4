<template>
  <div class="system-deployment-view">
    <!-- Right: Content Area -->
    <section class="detail-panel" v-loading="devicesLoading">
      <template v-if="selectedNode">
        <!-- ======= Device Detail View ======= -->
        <template v-if="viewMode === 'detail'">
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              <span>Deployed Device Details - {{ deviceForm.deviceDeployName }}</span>
            </div>
            <div class="header-actions">
              <el-button size="small" class="btn-secondary" :icon="Warning" @click="openDeviceAlarmConfig">Set Device Alarm</el-button>
              <el-button size="small" class="btn-secondary" :icon="Edit" @click="openEditDeviceDialog">Edit Device</el-button>
              <el-button size="small" class="btn-danger" :icon="Delete" @click="deleteDeviceFromDetail">Delete Device</el-button>
              <el-button size="small" class="btn-secondary btn-back" @click="goBackToList">Back to List</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- Device Properties Form -->
            <el-form
              :model="deviceForm"
              label-width="110px"
              label-position="right"
              class="custom-form-compact"
              disabled
            >
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="Device Name" prop="deviceDeployName">
                    <el-input v-model="deviceForm.deviceDeployName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Device Alias">
                    <el-input v-model="deviceForm.deviceAlias" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="Template">
                    <el-select v-model="deviceForm.deviceTmplId" placeholder="Select template" style="width: 100%">
                      <el-option
                        v-for="t in store.allTemplates"
                        :key="t.deviceTmplId"
                        :label="t.deviceTmplName"
                        :value="t.deviceTmplId!"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Device Type">
                    <el-input :value="deviceForm.deviceType || '-'" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="24">
                  <el-form-item label="N4 Topic">
                    <el-input :value="deviceForm.n4Address" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>

            <!-- Points Configuration Table -->
            <div class="point-table-section">
              <div class="points-header">
                <div class="section-title">
                  <el-icon class="section-icon"><List /></el-icon>
                  <span>Points Configuration (Total: {{ devicePoints.length }})</span>
                </div>
              </div>

              <el-table
                :data="devicePoints"
                style="width: 100%; margin-top: 6px;"
                border
                size="small"
                class="custom-table"
                :header-cell-style="headerCellStyle"
                :cell-style="cellStyle"
              >
                <el-table-column prop="pointName" label="Point Name" min-width="180" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column prop="type" label="Type" width="110" align="left" header-align="center" :resizable="false" />
                <el-table-column prop="unit" label="Unit" width="90" align="left" header-align="center" :resizable="false" />
                <el-table-column prop="modbusAddress" label="Modbus Address" min-width="150" align="left" header-align="center" :resizable="false">
                  <template #default="{ row }">
                    <code class="addr-code">{{ row.modbusAddress || 'N/A' }}</code>
                  </template>
                </el-table-column>
                <el-table-column label="Alarm Rule" min-width="160" align="left" header-align="center" :resizable="false">
                  <template #default="{ row }">
                    <div class="alarm-rule-cell">
                      <span
                        v-if="row.alarmCode"
                        class="alarm-code-tag"
                        title="Click to configure alarm rule"
                        @click="openPointAlarmConfig(row)"
                      >
                        {{ row.alarmCode }}
                      </span>
                      <span
                        v-else
                        class="alarm-none-tag"
                        title="Click to configure alarm rule"
                        @click="openPointAlarmConfig(row)"
                      >
                        Unconfigured
                      </span>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </template>

        <!-- ======= List / Overview View ======= -->
        <template v-else>
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="node-icon" :class="{ 'real-data-icon': selectedNode.hasRealData }" v-if="getNodeIcon(selectedNode)">
                <component :is="getNodeIcon(selectedNode)" />
              </el-icon>
              <span>{{ selectedNode.nodeName }}</span>
            </div>
            <div class="header-actions">
              <el-button size="small" class="btn-primary" :icon="Plus" v-if="canAddChild(selectedNode)" @click="navNodeDialogRef?.openCreate(selectedNode)">Add Child</el-button>
              <el-button size="small" class="btn-secondary" :icon="EditPen" @click="navNodeDialogRef?.openEdit(selectedNode)">Edit</el-button>
              <el-button size="small" class="btn-danger" :icon="Delete" @click="navNodeDialogRef?.confirmDelete(selectedNode)">Delete</el-button>
              <el-button size="small" class="btn-secondary" :icon="Download" @click="openDeploymentImportDialog">Import</el-button>
              <el-button size="small" class="btn-secondary" :icon="Upload" @click="exportDeploymentSnapshot">Export</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- Red Box Area: Interactive Cabin Diagram for Cabin Nodes -->
            <div class="cabin-diagram-card" v-if="cabinImageUrl">
              <div class="cabin-diagram-header">
                <div class="cabin-header-left">
                  <el-icon class="cabin-icon"><Picture /></el-icon>
                  <span class="cabin-title">Cabin Diagram - {{ selectedNode.nodeName }}</span>
                  <span class="zoom-badge">{{ Math.round(cabinZoomScale * 100) }}%</span>
                </div>
                <div class="cabin-header-right">
                  <span class="cabin-hint">💡 Scroll mouse wheel to zoom, drag to pan, double-click to reset</span>
                  <div class="zoom-actions">
                    <el-button size="small" class="btn-icon" :icon="ZoomIn" title="Zoom In" @click="zoomInCabin" />
                    <el-button size="small" class="btn-icon" :icon="ZoomOut" title="Zoom Out" @click="zoomOutCabin" />
                    <el-button size="small" class="btn-icon" :icon="RefreshRight" title="Reset Zoom" @click="resetCabinZoom" />
                  </div>
                </div>
              </div>
              <div
                class="cabin-image-wrapper"
                :class="{ 'is-dragging': isCabinDragging }"
                @wheel.prevent="handleCabinWheel"
                @mousedown="handleCabinMouseDown"
                @mousemove="handleCabinMouseMove"
                @mouseup="handleCabinMouseUp"
                @mouseleave="handleCabinMouseUp"
                @dblclick="resetCabinZoom"
              >
                <div class="cabin-stage" :style="cabinStageStyle">
                  <img
                    :src="cabinImageUrl"
                    alt="Cabin Diagram"
                    class="cabin-diagram-img"
                    draggable="false"
                  />
                </div>
              </div>
            </div>

            <!-- Leaf Node: Deployed Device List -->
            <div v-if="isLeafNode(selectedNode)" class="device-section">
              <div class="section-header">
                <h3>Deployed Devices
                  <span class="section-count">(Total: {{ currentDevices.length }})</span>
                </h3>
                <div class="header-actions">
                  <template v-if="isBatchManaging">
                    <el-button
                      type="danger"
                      size="small"
                      class="btn-danger"
                      :icon="Delete"
                      :disabled="selectedDevices.length === 0"
                      @click="batchDeleteDevices"
                    >
                      Batch Delete ({{ selectedDevices.length }} selected)
                    </el-button>
                    <el-button size="small" class="btn-secondary" @click="exitBatchManage">Exit Manage</el-button>
                  </template>
                  <template v-else>
                    <el-button size="small" class="btn-secondary" :icon="Edit" @click="enterBatchManage">
                      Batch Manage
                    </el-button>
                  </template>
                  <el-button size="small" class="btn-primary" :icon="Plus" @click="openDeployDialog">
                    Deploy Device
                  </el-button>
                  <el-button size="small" class="btn-secondary" :icon="Link" @click="openMountDialog">
                    Mount Device
                  </el-button>
                </div>
              </div>

              <!-- Search Bar -->
              <div class="search-box-table">
                <el-input
                  v-model="searchQuery"
                  placeholder="Search device name or alias..."
                  size="small"
                  :prefix-icon="Search"
                  clearable
                  class="search-bar"
                />
              </div>

              <!-- Device Table -->
              <el-table
                :data="filteredDevices"
                class="custom-table"
                border
                size="small"
                :header-cell-style="headerCellStyle"
                :cell-style="cellStyle"
                @selection-change="handleDeviceSelectionChange"
              >
                <el-table-column v-if="isBatchManaging" type="selection" width="30" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="deviceDeployId" label="ID" width="55" align="left" header-align="center" :resizable="false" />
                <el-table-column prop="deviceDeployName" label="Device Name" min-width="180" align="left" header-align="center" show-overflow-tooltip :resizable="false">
                  <template #default="{ row }">
                    <el-link type="primary" class="device-link" @click="viewDeviceDetail(row as DeviceDeployment)">
                      {{ row.deviceDeployName }}
                    </el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="deviceAlias" label="Device Alias" min-width="140" align="left" header-align="center" show-overflow-tooltip :resizable="false">
                  <template #default="{ row }">
                    <span>{{ row.deviceAlias || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="deviceType" label="Device Type" width="110" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column prop="manufacturer" label="Manufacturer" width="120" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column label="Status" width="90" align="left" header-align="center" :resizable="false">
                  <template #default="{ row }">
                    <span class="status-dot" :class="getStatusClass(row.status)"></span>
                    <span class="status-text">{{ getStatusText(row.status) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="Actions" width="160" align="center" header-align="center" fixed="right" :resizable="false">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="viewDeviceDetail(row as DeviceDeployment)">Edit</el-button>
                    <el-button type="primary" link size="small" @click="handleUnmount(row as DeviceDeployment)">Unmount</el-button>
                    <el-button type="danger" link size="small" @click="deleteDeviceFromTable(row as DeviceDeployment)">Delete</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <span class="empty-text">No deployed devices under this node</span>
                </template>
              </el-table>
            </div>

            <!-- Non-leaf Node: Sub-nodes Overview -->
            <div v-else class="device-section">
              <div class="section-header">
                <h3>Child Groups <span class="section-count">({{ selectedNode.children?.length || 0 }})</span></h3>
                <div class="header-actions" v-if="canAddChild(selectedNode)">
                  <el-button size="small" class="btn-primary" :icon="Plus" @click="navNodeDialogRef?.openCreate(selectedNode)">
                    Add Child Group
                  </el-button>
                </div>
              </div>
              <el-table :data="selectedNode.children || []" class="custom-table" border size="small" :header-cell-style="headerCellStyle" :cell-style="cellStyle">
                <el-table-column prop="nodeName" label="Group Name" min-width="200" align="left" header-align="center" :resizable="false" />
                <el-table-column prop="deviceCount" label="Devices" width="100" align="left" header-align="center" :resizable="false" />
                <el-table-column prop="sortOrder" label="Sort Order" width="100" align="left" header-align="center" :resizable="false" />
                <template #empty>
                  <span class="empty-text">No child groups under this node</span>
                </template>
              </el-table>
            </div>
          </div>
        </template>
      </template>

      <div v-else class="empty-selection-container">
        <el-empty description="Please select a navigation node from the left tree" :image-size="100" class="select-prompt">
          <el-button type="primary" class="btn-primary" :icon="Plus" @click="navNodeDialogRef?.openCreate(null)">
            Add Root Node
          </el-button>
        </el-empty>
      </div>
    </section>

    <!-- Deployment CSV or ZIP import/export dialog -->
    <el-dialog
      v-model="deploymentImportDialogVisible"
      title="Import Deployment Data"
      width="920px"
      append-to-body
      class="dark-dialog"
    >
      <div
        class="deployment-upload-drop-zone"
        @dragover.capture="handleDeploymentDragOver"
        @drop.capture="handleDeploymentDrop"
      >
        <el-upload
          ref="deploymentUploadRef"
          class="upload-drag-area"
          drag
          multiple
          action="#"
          :auto-upload="false"
          accept=".csv,.zip"
          :on-change="handleDeploymentFileChange"
          :on-remove="handleDeploymentFileRemove"
        >
          <el-icon class="el-icon--upload" style="font-size: 48px; color: #3b82f6; margin-bottom: 12px;"><UploadFilled /></el-icon>
          <div class="el-upload__text" style="color: #cbd5e1; font-size: 14px;">
            Drag deployment CSV files, a ZIP archive, or a folder here, or <em style="color: #3b82f6; font-style: normal; font-weight: bold; cursor: pointer;">click to upload</em>
          </div>
          <template #tip>
            <div class="el-upload__tip text-center" style="margin-top: 15px; color: #64748b; font-size: 12px; text-align: center;">
              Download the complete CSV layout below, then replace the example row. The full batch is validated before any database write.
              Existing rows are reused. New/reused counts are based on CSV business keys (*path and mqttPath), not folder names.
              Importing deployment data does not create navigation nodes. Use Add Child Group for new sidebar folders.
            </div>
          </template>
        </el-upload>
      </div>
      <div class="deployment-folder-picker">
        <input
          ref="deploymentFolderInputRef"
          class="deployment-folder-input"
          type="file"
          accept=".csv"
          multiple
          webkitdirectory
          @change="handleDeploymentFolderChange"
        />
        <el-button size="small" class="btn-secondary" :icon="FolderOpened" @click="openDeploymentFolderPicker">
          Select Folder
        </el-button>
        <span v-if="deploymentFolderFileCount" class="deployment-folder-summary">
          {{ deploymentFolderName }}: {{ deploymentFolderFileCount }} CSV file(s) selected
          <span v-if="deploymentFolderDuplicateCount">({{ deploymentFolderDuplicateCount }} duplicate file(s) ignored)</span>
        </span>
      </div>
      <div v-if="deploymentImportResult" class="transfer-result">
        <div class="preview-result-header">
          <div>
            <div class="preview-result-title">Import Preview</div>
            <div class="preview-result-meta">
              <span>{{ deploymentImportResult.sourceFileCount }} file(s)</span>
              <span>•</span>
              <span>{{ deploymentImportResult.sourceRowCount }} row(s)</span>
            </div>
          </div>
          <el-tag
            size="small"
            :type="deploymentImportResult.valid ? 'success' : 'danger'"
            effect="dark"
          >
            {{ deploymentImportResult.valid ? 'Ready to confirm' : 'Action required' }}
          </el-tag>
        </div>

        <section class="preview-section">
          <div class="preview-section-heading">
            <span>Summary Statistics</span>
            <span class="preview-section-caption">Import impact by resource</span>
          </div>
          <el-table
            :data="deploymentSummaryTableData"
            class="custom-table preview-summary-table"
            border
            size="small"
            :header-cell-style="headerCellStyle"
            :cell-style="cellStyle"
          >
            <el-table-column prop="category" label="Category" min-width="180" />
            <el-table-column prop="created" label="New" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.created !== null" size="small" type="success" effect="dark">+{{ row.created }}</el-tag>
                <span v-else class="preview-empty-value">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="reused" label="Reused" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.reused !== null" size="small" type="info" effect="dark">{{ row.reused }}</el-tag>
                <span v-else class="preview-empty-value">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="details" label="Details / Total" min-width="220" show-overflow-tooltip />
            <el-table-column prop="statusText" label="Status" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.statusType" effect="plain">{{ row.statusText }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="deploymentPreviewTreeData.length" class="preview-section">
          <div class="preview-section-heading">
            <span>Parsed Device Deployments</span>
            <span class="preview-section-caption">
              {{ deploymentImportResult.deploymentPreviews?.length ?? 0 }} device(s) grouped by system
            </span>
          </div>
          <el-table
            :data="deploymentPreviewTreeData"
            class="custom-table preview-device-tree"
            row-key="id"
            border
            size="small"
            max-height="280"
            default-expand-all
            :tree-props="{ children: 'children' }"
            :header-cell-style="headerCellStyle"
            :cell-style="cellStyle"
          >
            <el-table-column label="System / Device" min-width="330" show-overflow-tooltip>
              <template #default="{ row }">
                <div v-if="row.rowType === 'system'" class="preview-system-cell">
                  <span class="preview-system-name">{{ row.systemName }}</span>
                  <span class="preview-system-meta">
                    {{ row.deviceCount }} device(s) · {{ row.pointCount }} point(s)
                  </span>
                </div>
                <div v-else class="preview-device-cell">
                  <span class="preview-device-name">{{ row.deviceName }}</span>
                  <span class="preview-device-path">{{ row.devicePath }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="sourceFile" label="File" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.rowType === 'device'">{{ row.sourceFile }}</span>
                <span v-else class="preview-empty-value">Grouped</span>
              </template>
            </el-table-column>
            <el-table-column prop="templateName" label="Matched Template" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.rowType === 'device'">{{ row.templateName || '-' }}</span>
                <span v-else class="preview-empty-value">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="pointCount" label="Points" width="90" align="center" />
            <el-table-column prop="action" label="Import Action" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.rowType === 'system'" size="small" type="info" effect="plain">Group</el-tag>
                <el-tag v-else-if="row.action === 'CREATE'" size="small" type="success" effect="dark">New</el-tag>
                <el-tag v-else-if="row.action === 'REUSE'" size="small" type="info" effect="dark">Reused</el-tag>
                <el-tag v-else size="small" type="warning" effect="dark">Skipped</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="deploymentImportResult.issues?.length" class="preview-section preview-issues-section">
          <div class="preview-section-heading preview-section-heading--danger">
            <span>Validation Issues</span>
            <span class="preview-section-caption">
              {{ deploymentImportResult.issues.length }} issue(s) require attention
            </span>
          </div>
          <el-table
            :data="deploymentImportResult.issues"
            class="custom-table"
            border
            size="small"
            max-height="180"
            :header-cell-style="headerCellStyle"
            :cell-style="cellStyle"
          >
            <el-table-column prop="severity" label="Level" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.severity === 'ERROR' ? 'danger' : 'warning'" effect="dark">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="Code" width="160" show-overflow-tooltip />
            <el-table-column prop="fileName" label="File" width="130" show-overflow-tooltip />
            <el-table-column prop="rowNumber" label="Row" width="60" align="center">
              <template #default="{ row }">
                {{ row.rowNumber ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="message" label="Message" min-width="220" show-overflow-tooltip />
          </el-table>
        </section>
      </div>
      <template #footer>
        <div class="transfer-dialog-footer" style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
          <el-button type="primary" link :icon="Download" :loading="deploymentTemplateDownloadLoading" @click="downloadDeploymentImportTemplate">
            Download CSV Template
          </el-button>
          <div>
            <el-button class="btn-secondary" @click="closeDeploymentImportDialog">Close</el-button>
            <el-button
              class="btn-secondary"
              :loading="deploymentImportLoading"
              :disabled="!deploymentImportFiles.length"
              @click="previewDeploymentImport"
            >
              Preview
            </el-button>
            <el-button
              type="primary"
              class="btn-primary"
              :loading="deploymentImportLoading"
              :disabled="!deploymentImportResult?.valid"
              @click="confirmDeploymentImport"
            >
              Confirm
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- Nav Node Dialog for Edit/Delete actions -->
    <NavNodeDialog ref="navNodeDialogRef" />

    <!-- ===== Dialog: Mount Existing Device ===== -->
    <el-dialog
      v-model="mountDialogVisible"
      title="Mount Existing Devices to Current Node"
      width="640px"
      append-to-body
      class="dark-dialog"
    >
      <el-table
        :data="mountableDevices"
        class="custom-table"
        border
        size="small"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        @selection-change="handleMountSelectionChange"
        max-height="360"
      >
        <el-table-column type="selection" width="35" align="center" header-align="center" :resizable="false" />
        <el-table-column prop="deviceDeployId" label="Device ID" min-width="90" align="left" header-align="center" :resizable="false" />
        <el-table-column prop="deviceDeployName" label="Device Name" min-width="220" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
        <el-table-column prop="deviceType" label="Device Type" min-width="100" align="left" header-align="center" :resizable="false" />
        <template #empty>
          <span class="empty-text">No mountable devices available (all devices are mounted)</span>
        </template>
      </el-table>
      <template #footer>
        <span class="mount-selected-tip" v-if="mountSelectedIds.length">
          {{ mountSelectedIds.length }} device(s) selected
        </span>
        <el-button class="btn-secondary" @click="mountDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          class="btn-primary"
          @click="submitMount"
          :loading="mountSubmitting"
          :disabled="!mountSelectedIds.length"
        >
          Mount Selected Devices
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Deploy New Device (from template) ===== -->
    <el-dialog
      v-model="deployDialogVisible"
      title="Deploy New Device to Current Node"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="deployForm"
        :rules="deployRules"
        ref="deployFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Target Node">
          <el-input :value="selectedNode?.nodeName" disabled />
        </el-form-item>

        <el-form-item label="Device Template" prop="deviceTmplId">
          <el-select v-model="deployForm.deviceTmplId" placeholder="Select device template" style="width: 100%" clearable>
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Device Name" prop="deviceDeployName">
          <el-input v-model="deployForm.deviceDeployName" maxlength="64" placeholder="e.g. A1_PTU_A1_LVP_A1_00_Collector_00" />
        </el-form-item>

        <el-form-item label="Device Alias" prop="deviceAlias">
          <el-input v-model="deployForm.deviceAlias" maxlength="64" placeholder="Defaults to device name" />
        </el-form-item>

        <el-form-item label="IP Address" prop="ipAddress">
          <el-input v-model="deployForm.ipAddress" maxlength="15" placeholder="e.g. 192.168.1.100" />
        </el-form-item>

        <el-form-item label="MAC Address" prop="macAddress">
          <el-input v-model="deployForm.macAddress" maxlength="17" placeholder="e.g. 00:1A:2B:3C:4D:5E" />
        </el-form-item>

        <el-form-item label="Install Date">
          <el-date-picker
            v-model="deployForm.installDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="deployDialogVisible = false">Cancel</el-button>
        <el-button class="btn-primary" size="small" type="primary" :loading="deploySubmitting" @click="submitDeploy">Deploy & Generate Points</el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Edit Deployed Device ===== -->
    <el-dialog
      v-model="editDeviceDialogVisible"
      title="Edit Deployed Device"
      width="520px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="editDeviceForm"
        :rules="editDeviceRules"
        ref="editDeviceFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Device Name" prop="deviceDeployName">
          <el-input v-model="editDeviceForm.deviceDeployName" maxlength="64" placeholder="Enter device name" />
        </el-form-item>

        <el-form-item label="Device Alias" prop="deviceAlias">
          <el-input v-model="editDeviceForm.deviceAlias" maxlength="64" placeholder="Enter device alias" />
        </el-form-item>

        <el-form-item label="Device Template" prop="deviceTmplId">
          <el-select
            v-model="editDeviceForm.deviceTmplId"
            placeholder="Select device template"
            style="width: 100%"
            @change="handleEditDialogTemplateChange"
          >
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Device Type">
          <el-input :value="editDeviceForm.deviceType || '-'" disabled />
        </el-form-item>

        <el-form-item label="N4 Topic">
          <el-input :value="editDeviceForm.n4Address" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="editDeviceDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          size="small"
          class="btn-primary"
          @click="submitEditDevice"
          :loading="editDeviceSubmitting"
        >
          Save
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, type CSSProperties } from 'vue'
import {
  Plus, EditPen, Delete,
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning,
  Cpu, Monitor, Box,
  FolderOpened,
  DataLine,
  Connection, Edit, List, Search, Link,
  ZoomIn, ZoomOut, RefreshRight, Download, Upload, UploadFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadInstance } from 'element-plus'
import { dashboardNavApi } from '@/api/dashboardNav'
import { deploymentApi } from '@/api/deployment'
import { useDashboardStore } from '@/stores/dashboard'
import type { ImportSummary, NavTreeNode, DeviceDeployItem, DeviceDeployment, PointDeploymentItem } from '@/api/types'
import NavNodeDialog from '@/views/device-deploy/dialogs/NavNodeDialog.vue'
import { triggerFormShake } from '@/utils/formAnimation'
import { buildDeploymentPreviewTree } from '@/utils/deploymentImportPreview'

const store = useDashboardStore()
const selectedNode = computed(() => store.currentNavNode)

const cabinImageUrl = computed(() => {
  if (!selectedNode.value) return ''
  // Category level nodes (nodeLevel === 1, e.g. A1_MVP, A1_PTU, A1_SLP) represent groupings and do NOT have cabin diagrams
  if (selectedNode.value.nodeLevel === 1) {
    return ''
  }
  const name = selectedNode.value.nodeName.trim()
  const normalized = name.replace(/[-_]/g, '').toLowerCase()

  // Match MVP cabin nodes (A1_MVP_A1, A1_MVP_B1, MVP_A1, MVP_B1, etc.)
  if (normalized.includes('mvpa1') || normalized.includes('mvpb1') || normalized.startsWith('a1mvp') || normalized.startsWith('mvp')) {
    return '/cabin-images/A1_MVP_A1.png'
  }
  return ''
})

// =====================================================================
//  Interactive Cabin Diagram Zoom & Pan
// =====================================================================
const cabinZoomScale = ref(1.0)
const cabinPanX = ref(0)
const cabinPanY = ref(0)
const isCabinDragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let initialPanX = 0
let initialPanY = 0

const cabinStageStyle = computed(() => ({
  transform: `translate(${cabinPanX.value}px, ${cabinPanY.value}px) scale(${cabinZoomScale.value})`,
  transformOrigin: 'center center',
  transition: isCabinDragging.value ? 'none' : 'transform 0.08s ease-out',
}))

function handleCabinWheel(e: WheelEvent) {
  const delta = e.deltaY < 0 ? 0.15 : -0.15
  const newScale = Math.min(Math.max(0.5, cabinZoomScale.value + delta), 4.0)
  cabinZoomScale.value = Number(newScale.toFixed(2))
  if (cabinZoomScale.value <= 1.0) {
    cabinPanX.value = 0
    cabinPanY.value = 0
  }
}

function handleCabinMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  isCabinDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  initialPanX = cabinPanX.value
  initialPanY = cabinPanY.value
}

function handleCabinMouseMove(e: MouseEvent) {
  if (!isCabinDragging.value) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  cabinPanX.value = initialPanX + dx
  cabinPanY.value = initialPanY + dy
}

function handleCabinMouseUp() {
  isCabinDragging.value = false
}

function zoomInCabin() {
  cabinZoomScale.value = Number(Math.min(4.0, cabinZoomScale.value + 0.25).toFixed(2))
}

function zoomOutCabin() {
  cabinZoomScale.value = Number(Math.max(0.5, cabinZoomScale.value - 0.25).toFixed(2))
  if (cabinZoomScale.value <= 1.0) {
    cabinPanX.value = 0
    cabinPanY.value = 0
  }
}

function resetCabinZoom() {
  cabinZoomScale.value = 1.0
  cabinPanX.value = 0
  cabinPanY.value = 0
}

watch(() => selectedNode.value?.nodeId, () => {
  resetCabinZoom()
})

const headerCellStyle: CSSProperties = {
  backgroundColor: '#0d1117',
  color: '#e5e7eb',
  fontSize: '10px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
  textAlign: 'center',
}

const cellStyle: CSSProperties = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
  borderBottom: '1px solid #1f2937',
  textAlign: 'left',
}

// =====================================================================
//  View Mode Management
// =====================================================================
/** 'list' = List/overview view, 'detail' = Device detail view */
const viewMode = ref<'list' | 'detail'>('list')

const navNodeDialogRef = ref<InstanceType<typeof NavNodeDialog>>()
const devicesLoading = ref(false)
const deploymentImportDialogVisible = ref(false)
const deploymentImportLoading = ref(false)
const deploymentTemplateDownloadLoading = ref(false)
const deploymentImportFiles = ref<File[]>([])
const deploymentImportResult = ref<ImportSummary | null>(null)
const deploymentUploadRef = ref<UploadInstance>()
const deploymentFolderInputRef = ref<HTMLInputElement>()
const deploymentFolderName = ref('')
const deploymentFolderFileCount = ref(0)
const deploymentFolderDuplicateCount = ref(0)

interface DeploymentSummaryRow {
  category: string
  created: number | null
  reused: number | null
  details: string
  statusText: string
  statusType: string
}

const deploymentSummaryTableData = computed<DeploymentSummaryRow[]>(() => {
  const res = deploymentImportResult.value
  if (!res) return []
  const list: DeploymentSummaryRow[] = [
    {
      // Navigation groups: exposes new navigation group counts and names for preview
      category: 'Navigation Groups',
      created: res.navigationGroupCreated,
      reused: null,
      details: res.navigationGroupNames?.length ? res.navigationGroupNames.join(', ') : 'No new groups',
      statusText: res.valid ? 'Ready' : 'Failed',
      statusType: res.valid ? 'success' : 'danger',
    },
    {
      category: 'Device Deployments',
      created: res.deviceDeploymentCreated,
      reused: res.deviceDeploymentReused,
      details: `Total: ${res.deviceDeploymentCreated + res.deviceDeploymentReused}`,
      statusText: res.valid ? 'Ready' : 'Failed',
      statusType: res.valid ? 'success' : 'danger',
    },
    {
      category: 'Point Deployments',
      created: res.pointDeploymentCreated,
      reused: res.pointDeploymentReused,
      details: `Total: ${res.pointDeploymentCreated + res.pointDeploymentReused}`,
      statusText: res.valid ? 'Ready' : 'Failed',
      statusType: res.valid ? 'success' : 'danger',
    },
    {
      category: 'Enum Alarm Rules',
      created: res.enumRuleCreated,
      reused: null,
      details: `Configured: ${res.enumRuleCreated}`,
      statusText: res.valid ? 'Ready' : 'Failed',
      statusType: res.valid ? 'success' : 'danger',
    },
    {
      category: 'Numeric Alarm Rules',
      created: res.numericRuleCreated,
      reused: null,
      details: `Configured: ${res.numericRuleCreated}`,
      statusText: res.valid ? 'Ready' : 'Failed',
      statusType: res.valid ? 'success' : 'danger',
    },
  ]
  if (res.skippedRowCount > 0) {
    list.push({
      category: 'Skipped Rows',
      created: null,
      reused: null,
      details: `Skipped: ${res.skippedRowCount} row(s)`,
      statusText: 'Warning',
      statusType: 'warning',
    })
  }
  return list
})

const deploymentPreviewTreeData = computed(() =>
  buildDeploymentPreviewTree(deploymentImportResult.value?.deploymentPreviews ?? []),
)
// Declared before the selectedNode watcher below: the watcher runs with
// { immediate: true } during setup, so it must not touch a variable that is
// still in the temporal dead zone (TDZ) or it throws
// "Cannot access 'currentDevices' before initialization".
const currentDevices = ref<DeviceDeployment[]>([])
const searchQuery = ref('')

function isLeafNode(node: NavTreeNode): boolean {
  return !node.children || node.children.length === 0
}

function canAddChild(node: NavTreeNode): boolean {
  const maxLevel = node.maxLevel ?? 2
  return node.nodeLevel < maxLevel
}

watch(selectedNode, async (newNode) => {
  viewMode.value = 'list'
  if (newNode && isLeafNode(newNode)) {
    await loadNodeDevices(newNode.nodeId)
  } else {
    currentDevices.value = []
  }
}, { immediate: true })

onMounted(async () => {
  if (!store.navTree || store.navTree.length === 0) {
    await store.loadNavTree()
  }
  if (!store.currentNavNode && store.navTree && store.navTree.length > 0) {
    store.currentNavNode = store.navTree[0]
  }
})

// =====================================================================
//  Icon Mapping
// =====================================================================
const iconMap: Record<string, any> = {
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning, EditPen,
  Cpu, Monitor, Box,
}
function getNodeIcon(node?: NavTreeNode | null) {
  if (!node) return null
  if (node.hasRealData) return DataLine
  if (node.nodeLevel > 1) return null
  const iconName = node.icon
  if (!iconName) return FolderOpened
  return iconMap[iconName] || FolderOpened
}

// =====================================================================
//  Device List
// =====================================================================

async function loadNodeDevices(nodeId: number) {
  devicesLoading.value = true
  try {
    currentDevices.value = await deploymentApi.listByNode(nodeId)
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load device list')
  } finally {
    devicesLoading.value = false
  }
}

const filteredDevices = computed(() => {
  let list = currentDevices.value
  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter(d =>
      (d.deviceDeployName && d.deviceDeployName.toLowerCase().includes(query)) ||
      (d.deviceAlias && d.deviceAlias.toLowerCase().includes(query))
    )
  }
  return list
})

// Batch Management
const selectedDevices = ref<DeviceDeployment[]>([])
const isBatchManaging = ref(false)

function handleDeviceSelectionChange(val: DeviceDeployment[]) {
  selectedDevices.value = val
}
function enterBatchManage() {
  isBatchManaging.value = true
  selectedDevices.value = []
}
function exitBatchManage() {
  isBatchManaging.value = false
  selectedDevices.value = []
}

// =====================================================================
//  Device Detail / Edit Dialog
// =====================================================================
const deviceForm = ref<any>({})
const devicePoints = ref<PointDeploymentItem[]>([])

const validateNoChinese = (fieldName: string, maxLen: number) => (_rule: any, value: any, callback: any) => {
  if (value !== undefined && value !== null && value !== '') {
    const trimmed = typeof value === 'string' ? value.trim() : ''
    if (trimmed.length > maxLen) {
      callback(new Error(`${fieldName} cannot exceed ${maxLen} characters`))
      return
    }
    if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
      callback(new Error(`${fieldName} cannot contain Chinese characters`))
      return
    }
  }
  callback()
}

function validateDeviceDeployName(_rule: any, value: any, callback: any) {
  const trimmed = typeof value === 'string' ? value.trim() : ''
  if (!trimmed) {
    callback(new Error('Please enter device name'))
    return
  }
  if (trimmed.length < 2 || trimmed.length > 64) {
    callback(new Error('Device name must be between 2 and 64 characters'))
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
    callback(new Error('Device name cannot contain Chinese characters'))
    return
  }
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_\-\.\s()]*$/.test(trimmed)) {
    callback(new Error('Device name contains invalid characters'))
    return
  }
  callback()
}

function validateIpAddress(_rule: any, value: any, callback: any) {
  if (value !== undefined && value !== null && value !== '') {
    const trimmed = typeof value === 'string' ? value.trim() : ''
    if (!trimmed) {
      callback()
      return
    }
    if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
      callback(new Error('IP address cannot contain Chinese characters'))
      return
    }
    const ipv4Regex = /^(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/
    const ipv6Regex = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/
    if (!ipv4Regex.test(trimmed) && !ipv6Regex.test(trimmed)) {
      callback(new Error('Invalid IP address format (e.g. 192.168.1.100)'))
      return
    }
  }
  callback()
}

function validateMacAddress(_rule: any, value: any, callback: any) {
  if (value !== undefined && value !== null && value !== '') {
    const trimmed = typeof value === 'string' ? value.trim() : ''
    if (!trimmed) {
      callback()
      return
    }
    if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
      callback(new Error('MAC address cannot contain Chinese characters'))
      return
    }
    if (!/^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/.test(trimmed)) {
      callback(new Error('Invalid MAC address format (e.g. 00:1A:2B:3C:4D:5E)'))
      return
    }
  }
  callback()
}

const editDeviceDialogVisible = ref(false)
const editDeviceSubmitting = ref(false)
const editDeviceForm = ref<any>({})
const editDeviceFormRef = ref<FormInstance>()

const editDeviceRules = ref<FormRules>({
  deviceDeployName: [
    { required: true, message: 'Please enter device name', trigger: 'blur' },
    { validator: validateDeviceDeployName, trigger: 'blur' },
    { max: 64, message: 'Device name cannot exceed 64 characters', trigger: 'blur' }
  ],
  deviceAlias: [
    { validator: validateNoChinese('Device alias', 64), trigger: 'blur' },
    { max: 64, message: 'Device alias cannot exceed 64 characters', trigger: 'blur' }
  ],
  deviceTmplId: [
    { required: true, message: 'Please select device template', trigger: 'change' }
  ],
  ipAddress: [
    { validator: validateIpAddress, trigger: 'blur' }
  ],
  macAddress: [
    { validator: validateMacAddress, trigger: 'blur' }
  ],
  location: [
    { validator: validateNoChinese('Location', 100), trigger: 'blur' },
    { max: 100, message: 'Location cannot exceed 100 characters', trigger: 'blur' }
  ],
  department: [
    { validator: validateNoChinese('Department', 50), trigger: 'blur' },
    { max: 50, message: 'Department cannot exceed 50 characters', trigger: 'blur' }
  ],
  responsiblePerson: [
    { validator: validateNoChinese('Responsible person', 50), trigger: 'blur' },
    { max: 50, message: 'Responsible person cannot exceed 50 characters', trigger: 'blur' }
  ],
  contactPhone: [
    { validator: validateNoChinese('Contact phone', 20), trigger: 'blur' },
    { max: 20, message: 'Contact phone cannot exceed 20 characters', trigger: 'blur' }
  ]
})

const deployRules = ref<FormRules>({
  deviceTmplId: [
    { required: true, message: 'Please select device template', trigger: 'change' }
  ],
  deviceDeployName: [
    { required: true, message: 'Please enter device name', trigger: 'blur' },
    { validator: validateDeviceDeployName, trigger: 'blur' },
    { max: 64, message: 'Device name cannot exceed 64 characters', trigger: 'blur' }
  ],
  deviceAlias: [
    { validator: validateNoChinese('Device alias', 64), trigger: 'blur' },
    { max: 64, message: 'Device alias cannot exceed 64 characters', trigger: 'blur' }
  ],
  ipAddress: [
    { validator: validateIpAddress, trigger: 'blur' }
  ],
  macAddress: [
    { validator: validateMacAddress, trigger: 'blur' }
  ]
})

async function viewDeviceDetail(row: DeviceDeployment) {
  devicesLoading.value = true
  try {
    const detail = await deploymentApi.getDetail(row.deviceDeployId!)
    deviceForm.value = { ...detail }
    devicePoints.value = await deploymentApi.getPoints(row.deviceDeployId!)
    viewMode.value = 'detail'
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load device details')
  } finally {
    devicesLoading.value = false
  }
}

function goBackToList() {
  viewMode.value = 'list'
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    loadNodeDevices(selectedNode.value.nodeId)
  }
}

function openEditDeviceDialog() {
  editDeviceForm.value = {
    deviceDeployId: deviceForm.value.deviceDeployId,
    deviceDeployName: deviceForm.value.deviceDeployName,
    deviceAlias: deviceForm.value.deviceAlias,
    deviceTmplId: deviceForm.value.deviceTmplId,
    deviceType: deviceForm.value.deviceType,
    n4Address: deviceForm.value.n4Address,
    navNodeId: deviceForm.value.navNodeId,
    alarmCode: deviceForm.value.alarmCode,
    status: deviceForm.value.status,
    ipAddress: deviceForm.value.ipAddress,
    macAddress: deviceForm.value.macAddress,
    installDate: deviceForm.value.installDate,
    warrantyExpire: deviceForm.value.warrantyExpire,
    location: deviceForm.value.location,
    department: deviceForm.value.department,
    responsiblePerson: deviceForm.value.responsiblePerson,
    contactPhone: deviceForm.value.contactPhone
  }
  editDeviceDialogVisible.value = true
}

function handleEditDialogTemplateChange(tmplId: number) {
  const tmpl = store.allTemplates.find(t => t.deviceTmplId === tmplId)
  if (tmpl) {
    editDeviceForm.value.deviceType = tmpl.deviceType
  }
}

async function submitEditDevice() {
  if (!editDeviceForm.value.deviceDeployId) {
    ElMessage.error('Cannot save: Missing device ID')
    return
  }
  if (!editDeviceFormRef.value) return
  const valid = await editDeviceFormRef.value.validate().catch(() => false)
  if (!valid) {
    triggerFormShake(editDeviceFormRef, 'Please check and correct the highlighted device fields')
    return
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to save changes to deployed device "${editDeviceForm.value.deviceDeployName}"?`,
      'Confirm Save',
      {
        confirmButtonText: 'Save',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  try {
    editDeviceSubmitting.value = true
    const payload = {
      deviceTmplId: editDeviceForm.value.deviceTmplId,
      navNodeId: editDeviceForm.value.navNodeId,
      deviceDeployName: editDeviceForm.value.deviceDeployName.trim(),
      deviceAlias: editDeviceForm.value.deviceAlias ? editDeviceForm.value.deviceAlias.trim() : editDeviceForm.value.deviceDeployName.trim(),
      alarmCode: editDeviceForm.value.alarmCode,
      ipAddress: editDeviceForm.value.ipAddress ? editDeviceForm.value.ipAddress.trim() : '',
      macAddress: editDeviceForm.value.macAddress ? editDeviceForm.value.macAddress.trim() : '',
      installDate: editDeviceForm.value.installDate,
      warrantyExpire: editDeviceForm.value.warrantyExpire,
      location: editDeviceForm.value.location ? editDeviceForm.value.location.trim() : '',
      department: editDeviceForm.value.department ? editDeviceForm.value.department.trim() : '',
      responsiblePerson: editDeviceForm.value.responsiblePerson ? editDeviceForm.value.responsiblePerson.trim() : '',
      contactPhone: editDeviceForm.value.contactPhone ? editDeviceForm.value.contactPhone.trim() : '',
      status: editDeviceForm.value.status
    }
    await deploymentApi.update(editDeviceForm.value.deviceDeployId, payload)
    await store.refreshAfterMutation()
    ElMessage.success('Device updated successfully')
    editDeviceDialogVisible.value = false
    await viewDeviceDetail(editDeviceForm.value)
  } catch (err: any) {
    ElMessage.error(err.message || 'Update failed')
  } finally {
    editDeviceSubmitting.value = false
  }
}

// =====================================================================
//  Device Delete
// =====================================================================
function deleteDeviceFromDetail() {
  ElMessageBox.confirm(`Are you sure you want to delete deployed device "${deviceForm.value.deviceDeployName}"? This will permanently delete the device and all its points.`, 'Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger',
    customClass: 'dark-message-box'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(deviceForm.value.deviceDeployId)
      ElMessage.success('Device deleted successfully')
      viewMode.value = 'list'
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || 'Failed to delete device')
    } finally {
      devicesLoading.value = false
    }
  }).catch(() => {})
}

function deleteDeviceFromTable(row: DeviceDeployment) {
  ElMessageBox.confirm(`Are you sure you want to delete deployed device "${row.deviceDeployName}"? This will permanently delete the device and all its points.`, 'Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger',
    customClass: 'dark-message-box'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(row.deviceDeployId!)
      ElMessage.success('Device deleted successfully')
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || 'Failed to delete device')
    } finally {
      devicesLoading.value = false
    }
  }).catch(() => {})
}

function batchDeleteDevices() {
  ElMessageBox.confirm(`Are you sure you want to delete ${selectedDevices.value.length} selected device(s)? This will permanently delete the devices and all their points.`, 'Batch Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger',
    customClass: 'dark-message-box'
  }).then(async () => {
    try {
      devicesLoading.value = true
      for (const d of selectedDevices.value) {
        await deploymentApi.remove(d.deviceDeployId!)
      }
      ElMessage.success('Batch delete completed successfully')
    } catch (e: any) {
      ElMessage.error(e.message || 'Batch delete failed')
    } finally {
      selectedDevices.value = []
      isBatchManaging.value = false
      await refreshAfterChange()
      devicesLoading.value = false
    }
  }).catch(() => {})
}

async function refreshAfterChange() {
  await store.refreshAfterMutation()
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    await loadNodeDevices(selectedNode.value.nodeId)
  }
}

function openDeploymentImportDialog() {
  deploymentUploadRef.value?.clearFiles()
  resetDeploymentFolderSelection()
  deploymentImportFiles.value = []
  deploymentImportResult.value = null
  deploymentImportDialogVisible.value = true
}

function resetDeploymentFolderSelection() {
  deploymentFolderName.value = ''
  deploymentFolderFileCount.value = 0
  deploymentFolderDuplicateCount.value = 0
  if (deploymentFolderInputRef.value) {
    deploymentFolderInputRef.value.value = ''
  }
}

function openDeploymentFolderPicker() {
  deploymentFolderInputRef.value?.click()
}

function getDroppedEntries(event: DragEvent): FileSystemEntry[] {
  return Array.from(event.dataTransfer?.items ?? [])
    .map(item => item.webkitGetAsEntry())
    .filter((entry): entry is FileSystemEntry => Boolean(entry))
}

function getDroppedDirectories(event: DragEvent): FileSystemDirectoryEntry[] {
  return getDroppedEntries(event)
    .filter((entry): entry is FileSystemDirectoryEntry => entry.isDirectory)
}

function handleDeploymentDragOver(event: DragEvent) {
  if (getDroppedDirectories(event).length) {
    event.preventDefault()
    event.stopPropagation()
  }
}

function handleDeploymentDrop(event: DragEvent) {
  const directories = getDroppedDirectories(event)
  if (!directories.length) return

  event.preventDefault()
  event.stopPropagation()
  void handleDeploymentDirectoryDrop(directories)
}

async function readDroppedEntry(entry: FileSystemEntry): Promise<File[]> {
  try {
    if (entry.isFile) {
      const fileEntry = entry as FileSystemFileEntry
      return [await new Promise<File>((resolve, reject) => {
        fileEntry.file(resolve, reject)
      })]
    }
    if (!entry.isDirectory) return []

    const reader = (entry as FileSystemDirectoryEntry).createReader()
    const entries: FileSystemEntry[] = []
    while (true) {
      const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => {
        reader.readEntries(resolve, reject)
      })
      if (!batch.length) break
      entries.push(...batch)
    }

    const nestedFiles = await Promise.all(entries.map(readDroppedEntry))
    return nestedFiles.flat()
  } catch {
    return []
  }
}

function isCsvFile(file: File): boolean {
  return file.name.toLowerCase().endsWith('.csv')
}

function isSupportedDeploymentFile(file: File): boolean {
  const fileName = file.name.toLowerCase()
  return fileName.endsWith('.csv') || fileName.endsWith('.zip')
}

async function fingerprintDeploymentFile(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let hash = 2166136261
  for (const byte of bytes) {
    hash ^= byte
    hash = Math.imul(hash, 16777619)
  }
  return `${file.size}:${hash >>> 0}`
}

async function deduplicateDeploymentFiles(files: File[]): Promise<File[]> {
  const seenFingerprints = new Set<string>()
  const uniqueFiles: File[] = []
  for (const file of files) {
    try {
      const fingerprint = await fingerprintDeploymentFile(file)
      if (seenFingerprints.has(fingerprint)) continue
      seenFingerprints.add(fingerprint)
    } catch {
      // Keep files that cannot be read so the backend can report the issue.
    }
    uniqueFiles.push(file)
  }
  return uniqueFiles
}

function applyDeploymentFolderSelection(csvFiles: File[], folderName: string, duplicateCount = 0) {
  deploymentUploadRef.value?.clearFiles()
  deploymentImportFiles.value = csvFiles
  deploymentImportResult.value = null
  deploymentFolderFileCount.value = csvFiles.length
  deploymentFolderDuplicateCount.value = duplicateCount
  deploymentFolderName.value = folderName || 'Selected folder'

  if (!csvFiles.length) {
    ElMessage.warning('No CSV files found in the selected folder')
  }
}

async function handleDeploymentDirectoryDrop(directories: FileSystemDirectoryEntry[]) {
  const nestedFiles = await Promise.all(directories.map(readDroppedEntry))
  const csvFiles = nestedFiles.flat().filter(isCsvFile)
  const uniqueCsvFiles = await deduplicateDeploymentFiles(csvFiles)
  const folderName = directories.length === 1 ? directories[0].name : 'Selected folders'
  applyDeploymentFolderSelection(uniqueCsvFiles, folderName, csvFiles.length - uniqueCsvFiles.length)
}

async function handleDeploymentFolderChange(event: Event) {
  const input = event.target as HTMLInputElement
  const selectedFiles = Array.from(input.files ?? [])
  const csvFiles = selectedFiles.filter(file => file.name.toLowerCase().endsWith('.csv'))
  const uniqueCsvFiles = await deduplicateDeploymentFiles(csvFiles)

  const firstRelativePath = uniqueCsvFiles[0]?.webkitRelativePath ?? ''
  applyDeploymentFolderSelection(
    uniqueCsvFiles,
    firstRelativePath.split('/')[0] || 'Selected folder',
    csvFiles.length - uniqueCsvFiles.length,
  )

  input.value = ''
}

function handleDeploymentFileChange(_uploadFile: any, uploadFiles: any[]) {
  deploymentImportFiles.value = uploadFiles
    .map(file => file.raw as File | undefined)
    .filter((file): file is File => Boolean(file))
    .filter(isSupportedDeploymentFile)
  resetDeploymentFolderSelection()
  deploymentImportResult.value = null
}

function handleDeploymentFileRemove(_uploadFile: any, uploadFiles: any[]) {
  handleDeploymentFileChange(_uploadFile, uploadFiles)
}

async function previewDeploymentImport() {
  if (!deploymentImportFiles.value.length) return
  deploymentImportLoading.value = true
  try {
    deploymentImportResult.value = await deploymentApi.importFiles(deploymentImportFiles.value, true)
  } catch (err: any) {
    deploymentImportResult.value = null
    ElMessage.error(err.message || 'Deployment data preview failed')
  } finally {
    deploymentImportLoading.value = false
  }
}

async function confirmDeploymentImport() {
  if (!deploymentImportResult.value?.valid || !deploymentImportFiles.value.length) return
  deploymentImportLoading.value = true
  try {
    const result = await deploymentApi.importFiles(deploymentImportFiles.value, false)
    if (!result.valid || !result.committed) {
      deploymentImportResult.value = result
      ElMessage.error('Deployment data import was not committed')
      return
    }
    await refreshAfterChange()
    ElMessage.success('Deployment data imported successfully')
    closeDeploymentImportDialog()
  } catch (err: any) {
    ElMessage.error(err.message || 'Deployment data import failed')
  } finally {
    deploymentImportLoading.value = false
  }
}

function closeDeploymentImportDialog() {
  deploymentImportDialogVisible.value = false
  deploymentUploadRef.value?.clearFiles()
  resetDeploymentFolderSelection()
  deploymentImportFiles.value = []
  deploymentImportResult.value = null
}

async function downloadDeploymentImportTemplate() {
  deploymentTemplateDownloadLoading.value = true
  try {
    const blob = await deploymentApi.downloadImportTemplate()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'n4-deployment-import-template.csv'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('CSV import template downloaded successfully')
  } catch (err: any) {
    ElMessage.error(err.message || 'CSV import template download failed')
  } finally {
    deploymentTemplateDownloadLoading.value = false
  }
}

async function exportDeploymentSnapshot() {
  try {
    await ElMessageBox.confirm(
      'Are you sure you want to export the deployment snapshot ZIP package?',
      'Export Confirmation',
      {
        confirmButtonText: 'Export',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  devicesLoading.value = true
  try {
    const blob = await deploymentApi.exportSnapshot()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'n4-deployment-snapshot.zip'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('Deployment snapshot downloaded successfully')
  } catch (err: any) {
    ElMessage.error(err.message || 'Deployment snapshot export failed')
  } finally {
    devicesLoading.value = false
  }
}

// =====================================================================
//  Device Deploy (Create)
// =====================================================================
const deployDialogVisible = ref(false)
const deploySubmitting = ref(false)
const deployFormRef = ref<FormInstance>()
const deployForm = ref<Partial<DeviceDeployment>>({
  deviceTmplId: undefined,
  deviceDeployName: '',
  deviceAlias: '',
  ipAddress: '',
  macAddress: '',
  installDate: ''
})

function openDeployDialog() {
  if (!selectedNode.value) {
    ElMessage.warning('Please select a deployment node from the left tree first!')
    return
  }
  deployForm.value = {
    deviceTmplId: undefined,
    deviceDeployName: '',
    deviceAlias: '',
    ipAddress: '',
    macAddress: '',
    installDate: ''
  }
  deployDialogVisible.value = true
  nextTick(() => {
    if (deployFormRef.value) {
      deployFormRef.value.clearValidate()
    }
  })
}

async function submitDeploy() {
  if (!deployFormRef.value) return
  const valid = await deployFormRef.value.validate().catch(() => false)
  if (!valid) {
    triggerFormShake(deployFormRef, 'Please check and correct the highlighted deployment fields')
    return
  }

  if (!selectedNode.value?.nodeId) {
    ElMessage.warning('Invalid target node, please select again.')
    return
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to deploy device "${deployForm.value.deviceDeployName}"? This will automatically generate all template points.`,
      'Confirm Deployment',
      {
        confirmButtonText: 'Deploy',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  deploySubmitting.value = true
  const payload: Partial<DeviceDeployment> = {
    deviceTmplId: deployForm.value.deviceTmplId || undefined,
    deviceDeployName: (deployForm.value.deviceDeployName || '').trim(),
    deviceAlias: deployForm.value.deviceAlias ? deployForm.value.deviceAlias.trim() : (deployForm.value.deviceDeployName || '').trim(),
    ipAddress: deployForm.value.ipAddress ? deployForm.value.ipAddress.trim() : undefined,
    macAddress: deployForm.value.macAddress ? deployForm.value.macAddress.trim() : undefined,
    installDate: deployForm.value.installDate || undefined,
    navNodeId: selectedNode.value.nodeId
  }
  try {
    await deploymentApi.create(payload)
    ElMessage.success('Device deployed and points generated successfully!')
    deployDialogVisible.value = false
    await refreshAfterChange()
  } catch (err: any) {
    ElMessage.error(err.message || 'Failed to deploy device')
  } finally {
    deploySubmitting.value = false
  }
}

// =====================================================================
//  Mount / Unmount Device
// =====================================================================
const mountDialogVisible = ref(false)
const mountSubmitting = ref(false)
const mountableDevices = ref<DeviceDeployItem[]>([])
const mountSelectedIds = ref<number[]>([])

async function openMountDialog() {
  mountSelectedIds.value = []
  mountDialogVisible.value = true
  try {
    mountableDevices.value = await dashboardNavApi.getMountableDevices()
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load mountable devices')
  }
}

function handleMountSelectionChange(rows: DeviceDeployItem[]) {
  mountSelectedIds.value = rows.map(r => r.deviceDeployId)
}

async function submitMount() {
  if (!selectedNode.value || !mountSelectedIds.value.length) return

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to mount ${mountSelectedIds.value.length} selected device(s) to "${selectedNode.value.nodeName}"?`,
      'Confirm Mount',
      {
        confirmButtonText: 'Mount',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  mountSubmitting.value = true
  try {
    const count = await dashboardNavApi.mountDevices(
      selectedNode.value.nodeId,
      mountSelectedIds.value,
    )
    ElMessage.success(`Successfully mounted ${count} device(s)`)
    mountDialogVisible.value = false
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || 'Mount failed')
  } finally {
    mountSubmitting.value = false
  }
}

async function handleUnmount(device: DeviceDeployment) {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to unmount device "${device.deviceDeployName}" from the current node?`,
      'Unmount Confirmation',
      {
        confirmButtonText: 'Unmount',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      },
    )
  } catch {
    return
  }
  try {
    await dashboardNavApi.unmountDevice(device.deviceDeployId!)
    ElMessage.success('Unmounted successfully')
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || 'Unmount failed')
  }
}

// =====================================================================
//  Helper Functions
// =====================================================================
function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'offline'
  }
}
function getStatusText(status: number): string {
  switch (status) {
    case 1: return 'Online'
    case 2: return 'Offline'
    case 3: return 'Maintenance'
    case 4: return 'Fault'
    default: return 'Unknown'
  }
}

function openDeviceAlarmConfig() {
  ElMessageBox.alert(`Configuring device-level alarm rules: When the device is offline or heartbeat fails, all sub-points report communication faults.`, 'Set Device Alarm', {
    confirmButtonText: 'OK'
  })
}
function openPointAlarmConfig(row: any) {
  ElMessageBox.alert(`Configuring point alarm rules for [${row.pointName}]: Thresholds, notification channels, etc.`, 'Point Alarm Rule', {
    confirmButtonText: 'OK'
  })
}

// =====================================================================
//  Initialization
// =====================================================================
onMounted(async () => {
  if (!store.navTree.length) {
    await store.loadNavTree()
  }
  if (!store.allTemplates.length) {
    await store.loadTemplates()
  }
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    await loadNodeDevices(selectedNode.value.nodeId)
  }
})
</script>

<style scoped lang="scss">
$bg-card: #111b27;
$bg-card-hover: #1e293b;
$border-color: #1e293b;
$border-card: #2d3748;
$text-primary: #e5e7eb;
$text-secondary: #9ca3af;
$text-muted: #64748b;
$accent-green: #10b981;
$accent-blue: #3b82f6;
$accent-yellow: #f59e0b;
$accent-red: #ef4444;
$bg-dark: #0b1119;

.system-deployment-view {
  display: flex;
  height: 100%;
  min-height: 0;
  width: 100%;
}

// ===== Right Panel =====
.detail-panel {
  flex: 1;
  width: 100%;
  min-width: 0;
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid #1f2937;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-primary;
  font-size: 11px;
  font-weight: 600;

  .el-icon {
    color: $accent-green;
    font-size: 13px;
  }
}

.title-icon {
  color: $accent-green;
  font-size: 13px;
}

.detail-header {
  .header-actions {
    display: flex;
    gap: 6px;
  }
}

.empty-selection-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cabin-diagram-card {
  width: 100%;
  background-color: $bg-dark;
  border: 1px solid $border-color;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.cabin-diagram-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 10px;
  background-color: rgba(30, 41, 59, 0.4);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);

  .cabin-header-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .cabin-icon {
      color: $accent-green;
      font-size: 13px;
    }

    .cabin-title {
      font-size: 11px;
      font-weight: 600;
      color: $text-primary;
    }

    .zoom-badge {
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 10px;
      background-color: rgba(59, 130, 246, 0.15);
      color: #60a5fa;
      font-weight: 600;
    }
  }

  .cabin-header-right {
    display: flex;
    align-items: center;
    gap: 10px;

    .cabin-hint {
      font-size: 10px;
      color: $text-muted;
    }

    .zoom-actions {
      display: flex;
      align-items: center;
      gap: 4px;

      .btn-icon {
        background-color: rgba(255, 255, 255, 0.05) !important;
        border: 1px solid rgba(255, 255, 255, 0.1) !important;
        color: $text-secondary !important;
        padding: 2px 6px !important;
        height: 20px !important;
        border-radius: 4px !important;
        font-size: 11px !important;

        &:hover {
          background-color: rgba(16, 185, 129, 0.15) !important;
          border-color: $accent-green !important;
          color: $accent-green !important;
        }
      }
    }
  }
}

.cabin-image-wrapper {
  width: 100%;
  height: 280px;
  padding: 0;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #06090e;
  overflow: hidden;
  position: relative;
  cursor: grab;
  user-select: none;

  &.is-dragging {
    cursor: grabbing;
  }

  .cabin-stage {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    pointer-events: auto;

    .cabin-diagram-img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      display: block;
      pointer-events: none;
      user-select: none;
    }
  }
}

.info-card {
  background-color: $bg-dark;
  border: 1px solid $border-color;
  border-radius: 4px;
  padding: 6px 10px;
}

.info-row {
  display: flex;
  padding: 2px 0;
  font-size: 10px;

  .info-label {
    width: 80px;
    color: $text-secondary;
    flex-shrink: 0;
  }
  .info-value {
    color: $text-primary;
  }
}

.device-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-height: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;

  h3 {
    margin: 0;
    font-size: 11px;
    color: $text-primary;
    font-weight: 600;
  }
  .section-count {
    color: $accent-green;
    font-size: 11px;
    font-weight: 500;
  }
}

.header-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.search-box-table {
  width: 240px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 22px !important;
    padding: 0 6px !important;
  }

  :deep(.el-input__inner) {
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
}

// ===== Device Details Form =====
.custom-form-compact {
  background-color: rgba($bg-card-hover, 0.3);
  border-radius: 6px;
  padding: 6px 10px 0;
  border: 1px solid $border-color;
  margin-bottom: 8px;
  flex-shrink: 0;

  :deep(.el-form-item) {
    margin-bottom: 6px;
  }
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    padding-right: 6px !important;
  }
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 20px !important;
    min-height: 20px !important;
    line-height: 20px !important;
    padding: 0 6px !important;
    border-radius: 4px !important;
    border: 1px solid $border-color !important;
    box-shadow: none !important;
  }
  :deep(.el-input.is-disabled .el-input__wrapper),
  :deep(.el-select.is-disabled .el-select__wrapper),
  :deep(.el-select__wrapper.is-disabled),
  :deep(.el-date-editor.is-disabled),
  :deep(.el-date-editor.is-disabled.el-input__wrapper) {
    background-color: rgba(11, 17, 25, 0.85) !important;
    border-color: rgba(30, 41, 59, 0.8) !important;
    box-shadow: none !important;
  }
  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    color: #e5e7eb !important;
  }
  :deep(.el-input.is-disabled .el-input__inner),
  :deep(.el-select.is-disabled .el-select__selected-item),
  :deep(.el-select__wrapper.is-disabled .el-select__selected-item),
  :deep(.el-date-editor.is-disabled .el-input__inner) {
    color: #94a3b8 !important;
    -webkit-text-fill-color: #94a3b8 !important;
  }
  :deep(.el-input__prefix), :deep(.el-input__suffix), :deep(.el-select__suffix) {
    font-size: 10px !important;
  }
}

.extra-property-buttons {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed rgba($border-color, 0.6);
  flex-shrink: 0;
}

.point-table-section {
  margin-top: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.points-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  flex-shrink: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: $text-primary;

  .section-icon {
    color: $accent-green;
    font-size: 13px;
  }
}

// ===== Table Style =====
.custom-table {
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #0d1117;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #e5e7eb;

  font-size: 10px !important;

  .el-table .cell {
    line-height: 20px;
  }

  .el-table__inner-wrapper {
    background-color: #111827;
  }

  .el-table__body-wrapper {
    background-color: #111827;
  }

  .el-table__header-wrapper {
    background-color: #0d1117;
  }

  .el-table__row {
    background-color: #111827;
  }

  .el-table__row:hover > td {
    background-color: #1a2332 !important;
  }

  td, th {
    border-bottom: 1px solid #1f2937;
  }

  :deep(.el-table__cell) {
    padding: 0 0 !important;
  }
  :deep(.el-table__cell .cell) {
    padding: 0 4px !important;
  }
  :deep(.el-table__header-cell .cell),
  :deep(th.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: center !important;
    text-align: center !important;
  }
  :deep(td.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    text-align: left !important;
  }
  :deep(td.el-table__cell.is-center .cell) {
    justify-content: center !important;
    text-align: center !important;
  }
}

.btn-back {
  margin-left: 20px !important;
}

.alarm-rule-cell {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  width: 100%;
  padding: 0 4px;
}

.alarm-code-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none !important;
  background-color: rgba($accent-red, 0.22) !important;
  color: #fca5a5 !important;
  cursor: pointer;
  transition: all 0.2s ease-in-out;

  &:hover {
    background-color: rgba($accent-red, 0.35) !important;
    color: #ffffff !important;
    transform: translateY(-0.5px);
  }
}

.alarm-none-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none !important;
  background-color: rgba(100, 116, 139, 0.14) !important;
  color: #94a3b8 !important;
  cursor: pointer;
  transition: all 0.2s ease-in-out;

  &:hover {
    background-color: rgba($accent-blue, 0.20) !important;
    color: #93c5fd !important;
    transform: translateY(-0.5px);
  }
}

.device-link {
  font-weight: 500;
  cursor: pointer;
  font-size: 10px;
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 1px 4px;
  border-radius: 2px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 10px;
  line-height: 16px;
}

// ===== Point Type Badge =====
.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none;
  transition: all 0.2s ease-in-out;

  &.type-di {
    background-color: rgba($accent-blue, 0.14);
    color: #7dd3fc;
  }
  &.type-do {
    background-color: rgba(168, 85, 247, 0.14);
    color: #c084fc;
  }
  &.type-ai {
    background-color: rgba($accent-green, 0.14);
    color: #4ade80;
  }
  &.type-ao {
    background-color: rgba($accent-yellow, 0.14);
    color: #facc15;
  }

  &:hover {
    filter: brightness(1.1);
    transform: translateY(-0.5px);
  }
}

// ===== Status Dot Indicator =====
.status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-right: 4px;
  &.online { background-color: $accent-green; box-shadow: 0 0 4px rgba(0, 230, 118, 0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 4px rgba(255, 213, 79, 0.6); }
  &.fault { background-color: #ff5252; box-shadow: 0 0 4px rgba(255, 82, 82, 0.6); }
}
.status-text {
  font-size: 10px;
  color: $text-secondary;
}

// ===== General =====
.empty-text {
  color: $text-muted;
  font-size: 10px;
}

.select-prompt {
  margin: auto;
  font-size: 10px;
}

.form-tip {
  font-size: 10px;
  color: $text-muted;
  margin-top: 4px;
  line-height: 1.4;
}

.mount-selected-tip {
  margin-right: auto;
  color: $accent-green;
  font-size: 10px;
}

.text-secondary {
  color: $text-secondary;
}

.custom-form {
  :deep(.el-form-item) {
    margin-bottom: 22px;

    &.is-error {
      margin-bottom: 24px;
    }
  }
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 22px !important;
    min-height: 22px !important;
    line-height: 22px !important;
    font-size: 10px !important;
  }
  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
}

.deployment-folder-input {
  display: none;
}

.deployment-folder-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.deployment-folder-summary {
  color: #94a3b8;
  font-size: 12px;
}

.transfer-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 20px;
}

.preview-result-header,
.preview-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-result-header {
  padding: 12px 14px;
  border: 1px solid rgba(59, 130, 246, 0.32);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(30, 64, 175, 0.22), rgba(15, 23, 42, 0.86));
}

.preview-result-title {
  color: $text-primary;
  font-size: 14px;
  font-weight: 700;
}

.preview-result-meta,
.preview-section-caption,
.preview-empty-value {
  color: $text-muted;
  font-size: 10px;
}

.preview-result-meta {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.preview-section {
  padding: 10px;
  border: 1px solid rgba(51, 65, 85, 0.8);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.46);
}

.preview-section-heading {
  margin-bottom: 8px;
  color: $text-primary;
  font-size: 12px;
  font-weight: 600;
}

.preview-section-heading--danger {
  color: #fca5a5;
}

.preview-system-cell,
.preview-device-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.preview-system-name {
  color: #93c5fd;
  font-weight: 700;
}

.preview-system-meta,
.preview-device-path {
  color: $text-muted;
  font-size: 10px;
}

.preview-device-name {
  color: $text-primary;
  font-weight: 600;
}

.preview-device-tree {
  :deep(.el-table__row--level-0) {
    background: rgba(30, 64, 175, 0.14);
  }

  :deep(.el-table__row--level-0:hover > td) {
    background: rgba(37, 99, 235, 0.22) !important;
  }
}

.preview-issues-section {
  border-color: rgba(248, 113, 113, 0.34);
  background: rgba(127, 29, 29, 0.12);
}

:deep(.el-dialog) {
  background-color: #151d2a !important;
  border: 1px solid #1f2937;
  border-radius: 8px;

  .el-dialog__title {
    color: $text-primary;
    font-size: 13px;
    font-weight: 600;
  }

  .el-dialog__body {
    border-top: 1px solid #1f2937;
    border-bottom: 1px solid #1f2937;
    padding: 16px 20px;
  }
}

:deep(.el-tag) {
  border: none !important;
  border-radius: 8px !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 0 6px !important;
  line-height: 16px !important;
  height: 16px !important;

  &.el-tag--success {
    background-color: rgba($accent-green, 0.14) !important;
    color: #4ade80 !important;
  }
  &.el-tag--warning {
    background-color: rgba($accent-yellow, 0.14) !important;
    color: #facc15 !important;
  }
  &.el-tag--info {
    background-color: rgba(100, 116, 139, 0.18) !important;
    color: #cbd5e1 !important;
  }
}

// Button styling lives in styles/global.scss (app-wide unified look).
</style>
