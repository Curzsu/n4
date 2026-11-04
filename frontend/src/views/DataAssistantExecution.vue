<template>
  <aside id="assistant-execution" class="execution" aria-labelledby="assistant-execution-title">
    <header class="panel-header">
      <h2 id="assistant-execution-title">Execution details</h2>
      <button type="button" class="close" aria-label="Close execution details" @click="emit('close')">
        <el-icon><Close /></el-icon>
      </button>
    </header>
    <div class="panel-body">
      <p class="question-label">QUESTION</p>
      <p class="question-text">{{ question }}</p>
      <p v-if="pending" class="panel-status" role="status">Waiting for the query response…</p>
      <p v-else-if="error" class="panel-status failed">Request failed. {{ error }}</p>
      <ol v-else-if="result" class="steps">
        <li>
          <details open>
            <summary>
              <strong>1. Plan the query</strong>
              <span v-if="result.execution">{{ formatDuration(result.execution.planningMillis) }}</span>
            </summary>
            <p v-if="result.execution" class="step-note">
              {{ result.execution.presetQuery ? 'Used a predefined query.' : 'Gemini mapped the question to a query.' }}
            </p>
            <dl>
              <div><dt>Data source</dt><dd>{{ datasetLabels[result.query.dataset] }}</dd></div>
              <div><dt>Operation</dt><dd>{{ operationLabels[result.query.operation] }}</dd></div>
              <div v-if="result.query.groupBy">
                <dt>Group by</dt><dd>{{ columnLabels[result.query.groupBy] ?? result.query.groupBy }}</dd>
              </div>
              <div v-if="result.query.startDate && result.query.endDate">
                <dt>Date range</dt>
                <dd>{{ result.query.startDate }} to {{ result.query.endDate }}<br />Asia/Shanghai</dd>
              </div>
              <div v-if="result.query.severity"><dt>Severity</dt><dd>{{ result.query.severity }}</dd></div>
              <div v-if="result.query.status"><dt>Status</dt><dd>{{ result.query.status }}</dd></div>
              <div v-if="result.query.search">
                <dt>{{ columnLabels[result.query.searchField ?? ''] ?? 'Search' }}</dt>
                <dd>{{ result.query.search }}</dd>
              </div>
            </dl>
          </details>
        </li>
        <li>
          <details open>
            <summary>
              <strong>2. Read monitoring data</strong>
              <span v-if="result.execution">{{ formatDuration(result.execution.queryMillis) }}</span>
            </summary>
            <dl>
              <div><dt>Matched records</dt><dd>{{ result.evidence.matchedCount.toLocaleString() }}</dd></div>
              <div v-if="result.query.operation !== 'count'">
                <dt>{{ result.query.operation === 'group' ? 'Groups shown' : 'Rows shown' }}</dt>
                <dd>{{ result.evidence.rows.length.toLocaleString() }}</dd>
              </div>
            </dl>
            <p v-if="result.evidence.truncated" class="step-note">The displayed results are limited.</p>
          </details>
        </li>
        <li v-if="result.execution">
          <details open>
            <summary>
              <strong>3. Summarize the result</strong>
              <span>{{ formatDuration(result.execution.summaryMillis) }}</span>
            </summary>
            <p class="step-note" :class="{ failed: !result.execution.summaryGenerated }">
              {{ result.execution.summaryGenerated
                ? 'Gemini summarized the query result.'
                : 'Gemini summary unavailable. The query result is available in the conversation.' }}
            </p>
          </details>
        </li>
      </ol>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import type { DataAnswer, Dataset } from '@/api/dataAssistant'

defineProps<{
  question: string
  pending: boolean
  error?: string
  result?: DataAnswer
  datasetLabels: Record<Dataset, string>
  columnLabels: Record<string, string>
}>()

const emit = defineEmits<{ close: [] }>()
const operationLabels: Record<DataAnswer['query']['operation'], string> = {
  count: 'Count records',
  group: 'Count by group',
  list: 'List records',
}

function formatDuration(milliseconds: number) {
  return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(2)} s`
}
</script>

<style scoped lang="scss">
.execution {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: $bg-dark;
  border-left: 1px solid $border-card;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid $border-card;
}

h2 { margin: 0; font-size: 15px; }
.close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: 1px solid $border-card;
  border-radius: 6px;
  color: $text-primary;
  cursor: pointer;
}
.close:hover { border-color: $accent-green; }
.panel-body { min-height: 0; overflow: auto; padding: 14px 16px 18px; }
.question-label { color: #65e9bd; font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.question-text { margin-top: 4px; overflow-wrap: anywhere; }
.panel-status { margin-top: 14px; color: $text-secondary; }
.steps { display: grid; gap: 10px; list-style: none; margin: 16px 0 0; padding: 0; }
details { padding: 9px 10px; border: 1px solid $border-card; border-radius: 8px; background: $bg-card; }
summary { cursor: pointer; }
summary span { float: right; margin-left: 6px; color: $text-secondary; font-size: 11px; }
.step-note { margin-top: 6px; color: $text-secondary; font-size: 12px; }
.failed { color: #fca5a5; }
dl { margin: 6px 0 0; font-size: 12px; }
dl > div { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 3fr); gap: 8px; padding: 3px 0; }
dt { color: $text-secondary; }
dd { margin: 0; overflow-wrap: anywhere; }

@media (max-width: 760px) {
  .execution { border-left: 0; border-top: 1px solid $border-card; }
}
</style>
