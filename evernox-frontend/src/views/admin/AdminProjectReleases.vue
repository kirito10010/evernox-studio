<template>
  <div class="project-releases">
    <header class="release-head">
      <div class="head-main">
        <el-button link class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回项目
        </el-button>
        <div class="title-line">
          <h1>{{ project?.name || '版本发布' }}</h1>
          <code>{{ project?.slug || '—' }}</code>
          <el-button v-if="project" link type="primary" size="small" @click="copyBase">
            复制更新地址
          </el-button>
        </div>
        <p v-if="project?.description" class="head-desc">{{ project.description }}</p>
        <div class="head-stats">
          <span><strong>{{ project?.releaseCount ?? 0 }}</strong> 版本</span>
          <span><strong>{{ project?.publishedCount ?? 0 }}</strong> 已发布</span>
          <span><strong>{{ draftCount }}</strong> 草稿</span>
          <span><strong>{{ project?.assetCount ?? 0 }}</strong> 附件</span>
          <span><strong>{{ formatBytes(project?.storageBytes ?? 0) }}</strong> 占用</span>
          <span><strong>{{ project?.downloadTotal ?? 0 }}</strong> 下载</span>
        </div>

      </div>
      <div class="head-actions">
        <el-button circle text :loading="loading" title="刷新" @click="loadAll">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建发布
        </el-button>
      </div>
    </header>

    <div class="latest-strip" :class="{ 'is-empty': !latestRelease }">
      <div class="latest-main">
        <span class="latest-label">最新</span>
        <template v-if="latestRelease">
          <code class="latest-tag">{{ latestRelease.tag }}</code>
          <span class="latest-title">{{ latestRelease.title }}</span>
          <span class="latest-meta">
            {{ formatDateTime(latestRelease.publishedAt) }} ·
            {{ latestRelease.assetCount }} 个附件 ·
            {{ formatBytes(latestRelease.totalSize) }} ·
            {{ latestRelease.downloadCount }} 次下载
          </span>
        </template>
        <template v-else>
          <span class="latest-empty">未设置最新版本</span>
          <span class="latest-meta">latest.yml 将返回 404</span>
        </template>
      </div>
      <div class="latest-actions">
        <code class="latest-url">{{ latestUrl }}</code>
        <el-button size="small" :disabled="!latestRelease" @click="copyLatest">
          复制 latest.yml
        </el-button>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索版本 tag / 标题"
          clearable
          :prefix-icon="Search"
          class="filter-keyword"
          @input="onKeywordInput"
          @clear="applyFilters"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-select" @change="applyFilters">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
        <el-button class="filter-reset" @click="resetFilters">重置</el-button>
        <span class="table-total">共 {{ total }} 个版本</span>
      </div>

      <el-table :data="releases" row-key="id" @row-click="onRowClick">
        <el-table-column label="版本 / 标题" min-width="300">
          <template #default="{ row }">
            <div class="release-cell">
              <div class="release-tag-line">
                <code class="release-tag" :class="{ 'is-latest': row.latest }">{{ row.tag }}</code>
                <el-tag v-if="row.latest" size="small" type="warning" effect="plain">最新</el-tag>
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.status === 1 ? '已发布' : '草稿' }}
                </el-tag>
              </div>
              <div class="release-title" :title="row.summary || row.title">{{ row.title }}</div>
            </div>

          </template>
        </el-table-column>

        <el-table-column label="附件" width="130">
          <template #default="{ row }">
            <span class="col-main">{{ row.assetCount }} 个</span>
            <div class="col-sub">{{ formatBytes(row.totalSize) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="下载" width="100">
          <template #default="{ row }">
            <span class="col-main">{{ row.downloadCount }}</span>
            <div class="col-sub">次</div>
          </template>
        </el-table-column>

        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">
            <span class="col-sub">{{ formatDateTime(row.publishedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button link type="primary" size="small" @click.stop="openEdit(rowAsRelease(row))">编辑</el-button>
              <el-button
                v-if="row.status === 0"
                link
                type="success"
                size="small"
                @click.stop="publish(rowAsRelease(row))"
              >
                发布
              </el-button>
              <el-button
                v-else
                link
                :type="row.latest ? 'warning' : 'primary'"
                size="small"
                @click.stop="toggleLatest(rowAsRelease(row))"
              >
                {{ row.latest ? '取消最新' : '设为最新' }}
              </el-button>
              <el-button link type="danger" size="small" @click.stop="removeRelease(rowAsRelease(row))">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有版本，点击右上角「新建发布」" :image-size="80" />
        </template>
      </el-table>

      <div class="table-footer">
        <span class="footer-total">共 {{ total }} 个版本</span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="loadReleases"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <ReleaseEditorDrawer
      v-model="editorVisible"
      :project-id="projectId"
      :project-slug="project?.slug || ''"
      :release-id="editingReleaseId"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  deleteRelease,
  getReleaseProject,
  getReleases,
  publishRelease,
  setReleaseLatest,
} from '@/api/adminRelease'
import type { ReleaseProject, ReleaseStatus, ReleaseSummary } from '@/types/release'
import { formatBytes } from '@/utils/image'
import { formatDateTime } from '@/utils/format'
import { useClipboard } from '@/composables/useClipboard'
import { releaseAbsoluteUrl, releaseBaseUrl, releaseLatestUrl } from '@/utils/release'
import ReleaseEditorDrawer from '@/components/release/ReleaseEditorDrawer.vue'

const route = useRoute()
const router = useRouter()
const { copy } = useClipboard()

const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const project = ref<ReleaseProject | null>(null)
const releases = ref<ReleaseSummary[]>([])
const latestRelease = ref<ReleaseSummary | null>(null)
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  status: '' as '' | ReleaseStatus,
})

const draftCount = computed(() =>
  Math.max((project.value?.releaseCount ?? 0) - (project.value?.publishedCount ?? 0), 0)
)

let searchTimer: number | undefined

const latestUrl = computed(() =>
  project.value ? releaseLatestUrl(project.value.slug) : '—'
)

function goBack() {
  router.push('/workspace/projects')

}

async function copyBase() {
  if (!project.value) return
  await copy(releaseAbsoluteUrl(releaseBaseUrl(project.value.slug)), '更新地址已复制')
}

async function copyLatest() {
  if (!project.value) return
  await copy(releaseAbsoluteUrl(releaseLatestUrl(project.value.slug)), 'latest.yml 地址已复制')
}

async function loadProject() {
  try {
    const res = await getReleaseProject(projectId.value)
    project.value = res.data
  } catch {
    /* 请求层已提示 */
  }
}

async function loadReleases() {
  loading.value = true
  try {
    const res = await getReleases({
      projectId: projectId.value,
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      status: filters.status === '' || filters.status == null ? undefined : filters.status,
      sort: 'latest',
    })
    releases.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

async function loadLatest() {
  try {
    const res = await getReleases({
      projectId: projectId.value,
      page: 1,
      size: 1,
      latest: true,
      sort: 'latest',
    })
    latestRelease.value = res.data?.records?.[0] || null
  } catch {
    latestRelease.value = null
  }
}

async function loadAll() {
  await Promise.all([loadProject(), loadReleases(), loadLatest()])
}

function onKeywordInput() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    page.value = 1
    void loadReleases()
  }, 300)
}

function applyFilters() {
  page.value = 1
  void loadReleases()
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  applyFilters()
}

function onSizeChange() {
  page.value = 1
  void loadReleases()
}

// ==================== 操作 ====================

async function publish(row: ReleaseSummary) {
  try {
    await ElMessageBox.confirm(
      `发布「${row.tag}」后，附件立即可被启动器下载${latestRelease.value ? '' : '，并自动设为最新版本'}。`,
      '发布版本',
      { type: 'warning', confirmButtonText: '发布' }
    )
    await publishRelease(row.id, !latestRelease.value)
    ElMessage.success('发布成功')
    await loadAll()
  } catch {
    /* 取消或请求层已提示 */
  }
}

async function toggleLatest(row: ReleaseSummary) {
  const next = !row.latest
  try {
    if (next) {
      await ElMessageBox.confirm(
        '切换后 latest.yml 将立即指向该版本，已有客户端会检测到这次更新。',
        '设为最新',
        { type: 'warning', confirmButtonText: '设为最新' }
      )
    } else {
      await ElMessageBox.confirm(
        '取消后 latest.yml 将返回 404，启动器会回退到 GitHub 更新源。',
        '取消最新',
        { type: 'warning', confirmButtonText: '取消最新' }
      )
    }
    await setReleaseLatest(row.id, next)
    ElMessage.success(next ? '已设为最新' : '已取消最新')
    await loadAll()
  } catch {
    /* 取消或请求层已提示 */
  }
}

async function removeRelease(row: ReleaseSummary) {
  try {
    await ElMessageBox.confirm(
      `将删除「${row.tag}」及其 ${row.assetCount} 个附件，文件不可恢复。`,
      '删除版本',
      { type: 'warning', confirmButtonText: '删除' }
    )
    await deleteRelease(row.id)
    ElMessage.success('版本已删除')
    await loadAll()
  } catch {
    /* 取消或请求层已提示 */
  }
}

// ==================== 编辑抽屉 ====================

const editorVisible = ref(false)
const editingReleaseId = ref<number | null>(null)

function openCreate() {
  editingReleaseId.value = null
  editorVisible.value = true
}

function onRowClick(row: ReleaseSummary) {
  openEdit(row)
}

function rowAsRelease(row: unknown): ReleaseSummary {
  return row as ReleaseSummary
}

function openEdit(row: ReleaseSummary) {
  editingReleaseId.value = row.id
  editorVisible.value = true
}

function onSaved() {
  void loadAll()
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.project-releases {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.release-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 22px;
  border-radius: 16px;
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  background:
    radial-gradient(120% 160% at 94% 0%, rgba(var(--ev-primary-rgb), 0.14), transparent 55%),
    var(--ev-bg-glass);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
}

.head-main {
  min-width: 0;
}

.back-btn {
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--ev-text-muted);
}

.title-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;

  h1 {
    font-size: 20px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  code {
    font-size: 11px;
    color: var(--ev-primary);
  }
}

.head-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ev-text-secondary);
}

.head-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 10px;
  font-size: 11px;
  color: var(--ev-text-muted);

  strong {
    margin-right: 3px;
    font-size: 14px;
    font-weight: 700;
    color: var(--ev-text-primary);
    font-variant-numeric: tabular-nums;
  }
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.latest-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 16px;
  border: 1px solid var(--ev-border-subtle);
  border-left: 3px solid var(--ev-primary);
  border-radius: 12px;
  background: var(--ev-bg-glass);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);

  &.is-empty {
    border-left-color: var(--el-color-warning);
  }
}

.latest-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.latest-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--ev-primary);
}

.latest-tag {
  font-size: 13px;
  font-weight: 800;
  color: var(--el-color-warning);
}

.latest-title {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ev-text-primary);
}

.latest-meta {
  font-size: 11px;
  color: var(--ev-text-muted);
}

.latest-empty {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-color-warning);
}

.latest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.latest-url {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10.5px;
  color: var(--ev-text-muted);
}

.table-card {
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 14px;
  background: var(--ev-bg-glass);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ev-border-subtle);

  .filter-keyword {
    flex: 3 1 200px;
    min-width: 0;
    max-width: 360px;
  }

  .filter-select {
    flex: 1 1 110px;
    max-width: 140px;
  }

  .filter-reset {
    flex-shrink: 0;
  }

  .table-total {
    margin-left: auto;
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

:deep(.el-table) {
  background: transparent;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(var(--ev-primary-rgb), 0.035);
  --el-table-border-color: var(--ev-border-subtle);
  --el-table-row-hover-bg-color: rgba(var(--ev-primary-rgb), 0.045);
  color: var(--ev-text-primary);
}

:deep(.el-table__inner-wrapper) {
  background: transparent;
}

:deep(.el-table th.el-table__cell) {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ev-text-muted);
}

:deep(.el-table td.el-table__cell) {
  padding: 10px 0;
}

:deep(.el-table__row) {
  cursor: pointer;
}

.release-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.release-tag-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.release-tag {
  padding: 1px 6px;
  border-radius: 5px;
  background: rgba(var(--ev-primary-rgb), 0.08);
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ev-primary);

  &.is-latest {
    background: rgba(230, 162, 60, 0.14);
    color: var(--el-color-warning);
  }
}

.release-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ev-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-main {

  font-size: 12.5px;
  font-weight: 600;
  color: var(--ev-text-primary);
}

.col-sub {
  font-size: 10.5px;
  color: var(--ev-text-muted);
}

.op-cell {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid var(--ev-border-subtle);

  .footer-total {
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

@media (max-width: 900px) {
  .release-head {
    padding: 16px 18px;
    flex-direction: column;
  }

  .head-actions {
    width: 100%;

    .el-button {
      flex: 1;
    }
  }

  .latest-strip {
    flex-direction: column;
    align-items: flex-start;

    .latest-actions {
      width: 100%;
      flex-wrap: wrap;

      .latest-url {
        max-width: 100%;
      }
    }
  }

  .table-toolbar {
    .filter-keyword {
      flex-basis: 100%;
      max-width: none;
    }

    .filter-select {
      max-width: none;
    }

    .table-total {
      margin-left: 0;
    }
  }

  .table-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
