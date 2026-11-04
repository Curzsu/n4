<template>
  <div class="assistant">
    <header class="page-header">
      <RouterLink to="/" class="back-link"><el-icon><ArrowLeft /></el-icon> N4 Monitor</RouterLink>
      <div class="header-actions">
        <span class="model">Gemini 3.5 Flash-Lite</span>
        <button v-if="turns.length" type="button" class="details-button"
                :aria-expanded="detailsOpen" aria-controls="assistant-execution"
                @click="detailsOpen = !detailsOpen">
          Execution details
        </button>
      </div>
    </header>

    <main ref="feed" class="feed" aria-label="Data Assistant conversation">
      <div class="content">
        <div v-if="!turns.length" class="welcome">
          <div class="assistant-icon"><el-icon><ChatDotRound /></el-icon></div>
          <p class="eyebrow">N4 MONITOR DATA ASSISTANT</p>
          <h1>What would you like to know?</h1>
          <p>Ask a question about your monitoring data and inspect the records behind the answer.</p>
          <div class="suggestions">
            <button
              v-for="example in examples" :key="example.question" type="button"
              @click="submit(example.question)"
            >
              <span>{{ example.label }}</span>
              {{ example.question }}
            </button>
          </div>
          <p v-if="!examples.length" class="muted">Your role has no available data sources.</p>
        </div>

        <article v-for="turn in turns" :key="turn.id" class="turn">
          <p class="question">{{ turn.question }}</p>
          <div class="response">
            <div class="response-header">
              <p class="eyebrow">DATA ASSISTANT</p>
              <button type="button" class="details-button" aria-controls="assistant-execution"
                      :aria-pressed="detailsOpen && selectedTurnId === turn.id"
                      @click="showDetails(turn.id)">
                View execution
              </button>
            </div>
            <p v-if="turn.pending" role="status" class="muted">Querying monitoring data…</p>
            <p v-else-if="turn.error" role="alert" class="error">{{ turn.error }}</p>
            <template v-else-if="turn.result">
              <p class="answer">{{ turn.result.answer }}</p>
              <div class="evidence">
                <div class="evidence-header">
                  <div>
                    <strong>{{ datasetLabels[turn.result.query.dataset] }}</strong>
                    <p v-if="turn.result.query.startDate" class="muted">
                      {{ turn.result.query.startDate }} to {{ turn.result.query.endDate }} · Asia/Shanghai
                    </p>
                  </div>
                  <span>{{ turn.result.evidence.matchedCount.toLocaleString() }} matched</span>
                </div>
                <p v-if="turn.result.query.severity || turn.result.query.status || turn.result.query.search" class="filters">
                  <span v-if="turn.result.query.severity">Severity · {{ turn.result.query.severity }}</span>
                  <span v-if="turn.result.query.status">Status · {{ turn.result.query.status }}</span>
                  <span v-if="turn.result.query.search">
                    {{ columnLabels[turn.result.query.searchField ?? ''] ?? 'Search' }} · {{ turn.result.query.search }}
                  </span>
                </p>
                <p v-if="turn.result.evidence.truncated" class="evidence-note">
                  Showing {{ turn.result.evidence.rows.length }}
                  {{ turn.result.query.operation === 'group' ? 'groups' : 'rows' }}.
                </p>
                <p v-if="!turn.result.evidence.rows.length" class="evidence-note">No matching records.</p>
                <div v-else class="table-wrap" tabindex="0" aria-label="Query results">
                  <table>
                    <thead>
                      <tr>
                        <th v-for="column in turn.result.evidence.columns" :key="column" scope="col">
                          {{ columnLabels[column] ?? column }}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, index) in turn.result.evidence.rows" :key="index">
                        <td v-for="column in turn.result.evidence.columns" :key="column">
                          {{ row[column] === '' ? '—' : row[column] }}
                          <span
                            v-if="column === 'count' && turn.result.query.operation === 'group'"
                            class="count-bar" aria-hidden="true"
                            :style="{ width: barWidth(row[column], turn.result.evidence.rows[0]?.count) }"
                          />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </template>
          </div>
        </article>
      </div>
    </main>

    <footer class="composer-area">
      <form class="composer" @submit.prevent="submit(input)">
        <label class="visually-hidden" for="data-question">Your question</label>
        <textarea
          id="data-question" v-model="input" rows="2" maxlength="500"
          placeholder="Ask about your monitoring data…" :disabled="!examples.length"
          @keydown="onComposerKey"
        />
        <div class="composer-actions">
          <button type="button" class="clear" :disabled="pending || !turns.length" @click="clearConversation">
            New conversation
          </button>
          <button type="submit" class="send" :disabled="pending || !input.trim() || !examples.length">
            {{ pending ? 'Querying…' : 'Ask Data' }}
          </button>
        </div>
      </form>
    </footer>
    <DataAssistantExecution
      v-if="detailsOpen && selectedTurn"
      :key="selectedTurn.id"
      class="execution-panel"
      :question="selectedTurn.question"
      :pending="selectedTurn.pending"
      :error="selectedTurn.error"
      :result="selectedTurn.result"
      :dataset-labels="datasetLabels"
      :column-labels="columnLabels"
      @close="detailsOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ArrowLeft, ChatDotRound } from '@element-plus/icons-vue'
import { askData, type DataAnswer, type Dataset } from '@/api/dataAssistant'
import { useAuthStore } from '@/stores/auth'
import DataAssistantExecution from './DataAssistantExecution.vue'

interface Turn {
  id: number
  question: string
  pending: boolean
  result?: DataAnswer
  error?: string
}

const datasetLabels: Record<Dataset, string> = {
  active_alarms: 'Active alarms',
  alarm_events: 'Historical alarm frames',
  deployments: 'Deployed devices',
}
const columnLabels: Record<string, string> = {
  time: 'Time', severity: 'Severity', source: 'Source', location: 'Location', status: 'Status',
  message: 'Message', stage: 'Stage', date: 'Date', device: 'Device', alias: 'Alias',
  deviceType: 'Device type', system: 'System', manufacturer: 'Manufacturer', model: 'Model',
  count: 'Count',
}
const starters = [
  { permission: 'alarm:view', label: 'ACTIVE ALARMS', question: 'Break down active alarms by severity.' },
  { permission: 'alarm:event:view', label: 'HISTORY', question: 'How many alarm frames were recorded today?' },
  { permission: 'device:deploy:view', label: 'DEVICES', question: 'How many deployed devices are there by type?' },
]

const auth = useAuthStore()
const examples = computed(() => starters.filter(({ permission }) => auth.hasPermission(permission)))
const feed = ref<HTMLElement>()
const input = ref('')
const turns = ref<Turn[]>([])
const pending = ref(false)
const detailsOpen = ref(true)
const selectedTurnId = ref<number>()
const selectedTurn = computed(() => turns.value.find(({ id }) => id === selectedTurnId.value))
let nextTurnId = 0

async function scrollToLatest() {
  await nextTick()
  const latest = feed.value?.querySelector('.turn:last-child')
  latest?.scrollIntoView({ block: 'start' })
}

async function submit(text: string) {
  const question = text.trim()
  if (!question || pending.value || !examples.value.length) return
  const context = turns.value.filter((turn) => turn.result).slice(-3).map((turn) => turn.question)
  const turn = ref<Turn>({ id: nextTurnId++, question, pending: true })
  turns.value.push(turn.value)
  selectedTurnId.value = turn.value.id
  pending.value = true
  input.value = ''
  void scrollToLatest()
  try {
    turn.value.result = await askData(question, context)
  } catch (error) {
    turn.value.error = error instanceof Error ? error.message : 'The query failed. Try again.'
  } finally {
    turn.value.pending = false
    pending.value = false
  }
}

function clearConversation() {
  turns.value = []
  input.value = ''
  selectedTurnId.value = undefined
  detailsOpen.value = true
}

function showDetails(turnId: number) {
  selectedTurnId.value = turnId
  detailsOpen.value = true
}

function onComposerKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void submit(input.value)
  }
}

function barWidth(value: string | number | undefined, largest: string | number | undefined) {
  return `${(Number(value) / (Number(largest) || 1)) * 100}%`
}
</script>

<style scoped lang="scss">
.assistant {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: radial-gradient(ellipse at top, #102520, transparent 48%), $bg-dark;
  font-size: 14px;
}

.page-header {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px clamp(16px, 4vw, 48px);
  border-bottom: 1px solid $border-card;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: $text-primary;
  text-decoration: none;
  font-weight: 650;
}

.model, .muted { color: $text-secondary; }
.model { font-size: 12px; }
.header-actions, .response-header { display: flex; align-items: center; gap: 10px; }
.header-actions { justify-content: flex-end; flex-wrap: wrap; }
.response-header { justify-content: space-between; margin-bottom: 8px; }
.feed { grid-area: 2 / 1; min-height: 0; overflow: auto; }
.execution-panel { grid-area: 2 / 2 / 4; width: clamp(290px, 30vw, 390px); }
.content { width: min(100% - 32px, 920px); margin: auto; padding: 28px 0; }
.welcome { padding: clamp(20px, 8vh, 84px) 0 12px; text-align: center; }
.welcome > p:not(.eyebrow) { color: $text-secondary; margin: 8px 0 24px; }
h1 { font-size: clamp(26px, 4vw, 44px); line-height: 1.15; margin: 12px 0; }

.assistant-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border: 1px solid #286454;
  border-radius: 14px;
  background: #12332b;
  color: #65e9bd;
  font-size: 23px;
}

.eyebrow { color: #65e9bd; font-size: 11px; letter-spacing: .12em; font-weight: 700; }
.suggestions { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; }

button {
  cursor: pointer;
  font: inherit;
  color: inherit;
  border: 1px solid $border-card;
  background: $bg-card;
  border-radius: 8px;
}

button:hover:enabled { border-color: $accent-green; }
button:disabled { opacity: .45; cursor: default; }
.details-button { padding: 4px 8px; background: transparent; color: $text-secondary; font-size: 12px; }
.details-button[aria-pressed='true'] { color: #65e9bd; border-color: #286454; }
.suggestions button { min-height: 76px; padding: 12px; text-align: left; }
.suggestions button span { display: block; color: #65e9bd; font-size: 10px; margin-bottom: 5px; }
.turn { display: grid; gap: 12px; margin-bottom: 24px; scroll-margin-top: 16px; }
.question, .answer { overflow-wrap: anywhere; white-space: pre-wrap; }
.question { justify-self: end; padding: 10px 14px; background: #17352e; border-radius: 12px; max-width: 90%; }
.response { min-width: 0; padding: 16px; background: $bg-card; border: 1px solid $border-card; border-radius: 12px; }
.error { color: #fca5a5; }
.evidence { margin-top: 14px; overflow: hidden; border: 1px solid $border-card; border-radius: 8px; }
.evidence-header { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; padding: 10px 12px; }
.evidence-header > span, .filters, .evidence-note { color: $text-secondary; font-size: 12px; }
.evidence-note, .filters { padding: 0 12px 10px; }
.filters { display: flex; flex-wrap: wrap; gap: 6px 14px; overflow-wrap: anywhere; }
.table-wrap { max-height: 340px; overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 12px; text-align: left; }
th, td { border-top: 1px solid $border-card; padding: 7px 12px; overflow-wrap: anywhere; }
th { position: sticky; top: 0; background: $bg-card-hover; color: $text-secondary; }
.count-bar { display: block; height: 3px; margin-top: 4px; background: $accent-green; border-radius: 4px; }
.composer-area { grid-area: 3 / 1; padding: 12px 16px 16px; border-top: 1px solid $border-card; }
.composer { max-width: 920px; margin: auto; border: 1px solid $border-card; border-radius: 12px; background: $bg-card; }
.composer:focus-within { border-color: $accent-green; }
textarea { display: block; width: 100%; resize: vertical; max-height: 28vh; padding: 12px; background: transparent; color: inherit; border: 0; outline: 0; font: inherit; }
.composer-actions { display: flex; justify-content: space-between; gap: 8px; padding: 0 10px 10px; }
.clear, .send { padding: 6px 12px; }
.send { background: #6ee7b7; border-color: transparent; color: #08251b; font-weight: 700; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }

@media (max-width: 760px) {
  .assistant {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr) auto auto;
  }

  .execution-panel { grid-area: 3 / 1; width: 100%; max-height: 36dvh; }
  .composer-area { grid-area: 4 / 1; }
}
</style>
