<template>
  <div class="admin-sites">
    <div class="page-header">
      <div class="header-text">
        <h2>网站审批</h2>
        <p>审批时可为站点打标签（可选），公开页按标签筛选</p>
      </div>
      <div class="stat-row">
        <div class="stat"><span class="num">{{ stats.pending ?? '—' }}</span><span class="label">待审批</span></div>
        <div class="stat"><span class="num">{{ stats.published ?? '—' }}</span><span class="label">已公开</span></div>
        <div class="stat"><span class="num">{{ stats.rejected ?? '—' }}</span><span class="label">已驳回</span></div>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="待审批" name="pending" />
      <el-tab-pane label="全部站点" name="all" />
      <el-tab-pane label="标签库" name="tag" />
    </el-tabs>

    <template v-if="activeTab !== 'tag'">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          class="search"
          placeholder="搜索名称、链接或介绍"
          clearable
          @input="onKeywordInput"
          @clear="applyFilters"
        />
        <el-input
          v-model="username"
          class="filter-item"
          placeholder="分享者"
          clearable
          @input="onUsernameInput"
          @clear="applyFilters"
        />
        <el-select
          v-if="activeTab === 'all'"
          v-model="statusFilter"
          class="filter-item"
          placeholder="全部状态"
          clearable
          @change="applyFilters"
        >
          <el-option label="私有" :value="0" />
          <el-option label="待审批" :value="1" />
          <el-option label="已公开" :value="2" />
          <el-option label="已驳回" :value="3" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="~"
          start-placeholder="提交起"
          end-placeholder="止"
          class="filter-date"
          @change="applyFilters"
        />
        <el-select v-model="sortField" class="filter-item" @change="applyFilters">
          <el-option label="提交时间" value="submittedAt" />
          <el-option label="创建时间" value="createdAt" />
          <el-option label="审批时间" value="reviewedAt" />
          <el-option label="权重" value="weight" />
          <el-option label="标题" value="title" />
        </el-select>
        <el-select v-model="sortOrder" class="filter-order" @change="applyFilters">
          <el-option label="降序" value="desc" />
          <el-option label="升序" value="asc" />
        </el-select>
      </div>

      <el-table :data="sites" v-loading="loading" row-key="id">
        <el-table-column label="图标" width="72">
          <template #default="{ row }">
            <div class="thumb-cell">
              <LazyImage
                v-if="row.coverImageId"
                :image-id="row.coverImageId"
                :loader="decryptImage"
                ratio="1 / 1"
                :alt="row.title"
              />
              <div v-else class="thumb-empty">
                <el-icon :size="16"><Link /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="站点" min-width="260">
          <template #default="{ row }">
            <div class="site-cell">
              <span class="site-title">{{ row.title }}</span>
              <a class="site-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
              <span class="site-desc" :title="row.description || ''">{{ row.description || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="分享者" width="120" />
        <el-table-column label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag.id" size="small" effect="plain" class="tag-chip">
              {{ tag.name }}
            </el-tag>
            <span v-if="!row.tags?.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="SiteStatusColor[row.status]" size="small">{{ SiteStatusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权重" width="80">
          <template #default="{ row }">{{ row.weight ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" type="primary" @click="openApprove(row as SiteLink)">通过</el-button>
            <el-button v-if="row.status === 1" size="small" type="danger" @click="handleReject(row as SiteLink)">驳回</el-button>
            <el-button v-if="row.status === 2" size="small" @click="openApprove(row as SiteLink)">改标签</el-button>
            <el-button v-if="row.status === 2" size="small" type="warning" @click="handleOffline(row as SiteLink)">撤下</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadSites"
          @size-change="applyFilters"
        />
      </div>
    </template>

    <template v-else>
      <div class="filter-bar">
        <el-button type="primary" @click="openTagDialog()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新增标签
        </el-button>
      </div>

      <el-table :data="tags" v-loading="tagLoading" row-key="id">
        <el-table-column prop="name" label="标签名" min-width="160" />
        <el-table-column prop="sort" label="排序" width="100" />
        <el-table-column prop="siteCount" label="关联站点" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="startEditTag(row as SiteTag)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDeleteTag(row as SiteTag)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="approveVisible" title="选择标签" width="520px" @closed="onApproveClosed">
      <div v-if="approveTarget" class="approve-site">
        <div class="approve-thumb">
          <LazyImage
            v-if="approveTarget.coverImageId"
            :image-id="approveTarget.coverImageId"
            :loader="decryptImage"
            ratio="1 / 1"
            :alt="approveTarget.title"
          />
          <div v-else class="thumb-empty">
            <el-icon :size="22"><Link /></el-icon>
          </div>
        </div>
        <div class="approve-info">
          <span class="approve-title">{{ approveTarget.title }}</span>
          <a
            class="site-url"
            :href="approveTarget.url"
            target="_blank"
            rel="noopener noreferrer"
          >{{ approveTarget.url }}</a>
          <p class="approve-desc">{{ approveTarget.description || '提交者未填写介绍' }}</p>
        </div>
      </div>
      <p class="dialog-hint">标签可选，公开页按标签筛选。可搜索、多选，或输入新标签名直接新建（默认排序 10）。</p>
      <div class="tag-picker">
        <div class="tag-picker-tools">
          <el-input v-model="tagSearch" placeholder="搜索标签" clearable class="tag-search" />
          <div class="tag-create">
            <el-input
              v-model="newTagName"
              placeholder="输入新标签名"
              maxlength="30"
              class="tag-create-input"
              @keyup.enter="addCustomTag"
            />
            <el-button size="small" :loading="customTagAdding" @click="addCustomTag">添加</el-button>
          </div>
        </div>
        <div class="tag-grid">
          <span
            v-for="tag in filteredTags"
            :key="tag.id"
            class="tag-option"
            :class="{ 'is-selected': approveTagIds.includes(tag.id) }"
            @click="toggleTag(tag.id)"
          >{{ tag.name }}</span>
          <span v-if="!filteredTags.length" class="muted">暂无标签</span>
        </div>
      </div>
      <div class="weight-row">
        <span class="weight-label">权重</span>
        <el-input-number v-model="approveWeight" :min="0" :step="1" :controls="false" class="weight-input" />
        <span class="weight-hint">数值越大越靠前</span>
      </div>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="confirmApprove">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tagDialogVisible" :title="editingTagId ? '编辑标签' : '新增标签'" width="420px">
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="tagForm.name" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="tagForm.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagSaving" @click="saveTag">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LazyImage from '@/components/LazyImage.vue'
import { useImageDecrypt } from '@/composables/useImageDecrypt'
import { getAdminImageBlob } from '@/api/adminAsset'
import {
  approveSite,
  createSiteTag,
  deleteSiteTag,
  getAdminSites,
  getAdminSiteStats,
  getAdminSiteTags,
  offlineSite,
  rejectSite,
  updateSiteTag,
  updateSiteTags,
  updateSiteWeight,
} from '@/api/adminSite'
import { SiteStatus, SiteStatusColor, SiteStatusMap } from '@/types/site'
import type { SiteLink, SiteStats, SiteTag } from '@/types/site'

const activeTab = ref('pending')
/**
 * 站点图标走管理员取流通道
 *
 * 待审批站点的封面此刻还是提交者的私密图，普通 `/image/{id}/file` 会 403，
 * 只有 `/admin/asset/image/{id}/file` 能读到。
 */
const { decryptImage, clearCache } = useImageDecrypt(getAdminImageBlob)
const sites = ref<SiteLink[]>([])
const loading = ref(false)
const keyword = ref('')
const username = ref('')
const statusFilter = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)
const sortField = ref<'createdAt' | 'submittedAt' | 'reviewedAt' | 'title' | 'weight'>('submittedAt')
const sortOrder = ref<'asc' | 'desc'>('desc')
const stats = ref<SiteStats>({ mine: null, pending: null, published: null, rejected: null })

const tags = ref<SiteTag[]>([])
const tagLoading = ref(false)
const tagSaving = ref(false)
const tagDialogVisible = ref(false)
const editingTagId = ref<number | null>(null)
const tagForm = reactive({ name: '', sort: 0 })

const approveVisible = ref(false)
const approving = ref(false)
const approveTagIds = ref<number[]>([])
const approveTarget = ref<SiteLink | null>(null)
const approveWeight = ref(0)
const tagSearch = ref('')
const newTagName = ref('')
const customTagAdding = ref(false)

const filteredTags = computed(() => {
  const kw = tagSearch.value.trim()
  if (!kw) return tags.value
  return tags.value.filter((t) => t.name.includes(kw))
})

const formatDateTime = (value: string): string => {
  if (!value) return '—'
  const d = new Date(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const loadSites = async () => {
  loading.value = true
  try {
    const res = await getAdminSites({
      page: currentPage.value,
      size: pageSize.value,
      // 待审批 Tab 固定过滤 status=1，全部 Tab 用下拉筛选
      status: activeTab.value === 'pending' ? SiteStatus.PENDING : statusFilter.value,
      keyword: keyword.value.trim() || undefined,
      username: username.value.trim() || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      sortField: sortField.value,
      sortOrder: sortOrder.value,
    })
    sites.value = res.data?.records || []
    total.value = res.data?.total || 0
    if (!sites.value.length && currentPage.value > 1) {
      currentPage.value -= 1
      await loadSites()
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

const loadTags = async () => {
  tagLoading.value = true
  try {
    const res = await getAdminSiteTags()
    tags.value = res.data || []
  } catch {
    /* 请求层已提示 */
  } finally {
    tagLoading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getAdminSiteStats()
    if (res.data) stats.value = res.data
  } catch {
    /* 请求层已提示 */
  }
}

const applyFilters = () => {
  currentPage.value = 1
  void loadSites()
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(applyFilters, 300)
}

let usernameTimer: ReturnType<typeof setTimeout> | null = null
const onUsernameInput = () => {
  if (usernameTimer) clearTimeout(usernameTimer)
  usernameTimer = setTimeout(applyFilters, 300)
}

const onTabChange = () => {
  currentPage.value = 1
  statusFilter.value = null
  if (activeTab.value === 'tag') void loadTags()
  else void loadSites()
}

const openApprove = (site: SiteLink) => {
  approveTarget.value = site
  approveTagIds.value = site.tags?.map((t) => t.id) || []
  approveWeight.value = site.weight || 0
  tagSearch.value = ''
  newTagName.value = ''
  approveVisible.value = true
}

/** 关闭动画结束后再清空，避免弹窗淡出过程中信息块突然消失 */
const onApproveClosed = () => {
  approveTarget.value = null
  approveTagIds.value = []
  approveWeight.value = 0
  tagSearch.value = ''
  newTagName.value = ''
}

const toggleTag = (id: number) => {
  const idx = approveTagIds.value.indexOf(id)
  if (idx === -1) approveTagIds.value.push(id)
  else approveTagIds.value.splice(idx, 1)
}

const addCustomTag = async () => {
  const name = newTagName.value.trim()
  if (!name) {
    ElMessage.warning('请输入新标签名')
    return
  }
  if (tags.value.some((t) => t.name === name)) {
    ElMessage.warning('该标签已存在')
    return
  }
  customTagAdding.value = true
  try {
    await createSiteTag({ name, sort: 10 })
    newTagName.value = ''
    await loadTags()
    // 自动选中刚新建的标签
    const created = tags.value.find((t) => t.name === name)
    if (created) toggleTag(created.id)
  } catch {
    /* 请求层已提示 */
  } finally {
    customTagAdding.value = false
  }
}

const confirmApprove = async () => {
  if (!approveTarget.value) return
  approving.value = true
  try {
    const weight = approveWeight.value || 0
    // 已公开站点走改标签+改权重接口，待审批走通过接口
    if (approveTarget.value.status === SiteStatus.PUBLIC) {
      await Promise.all([
        updateSiteTags(approveTarget.value.id, approveTagIds.value),
        updateSiteWeight(approveTarget.value.id, weight),
      ])
      ElMessage.success('标签与权重已更新')
    } else {
      await approveSite(approveTarget.value.id, approveTagIds.value, weight)
      ElMessage.success('已通过审批')
    }
    approveVisible.value = false
    await Promise.all([loadSites(), loadStats()])
  } catch {
    /* 请求层已提示 */
  } finally {
    approving.value = false
  }
}

const handleReject = async (site: SiteLink) => {
  let reason: string
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，用户可以看到：', '驳回申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value: string) =>
        (value && value.trim().length > 0) || '驳回原因不能为空',
    })
    reason = result.value
  } catch {
    return
  }
  try {
    await rejectSite(site.id, reason.trim())
    ElMessage.success('已驳回')
    await Promise.all([loadSites(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const handleOffline = async (site: SiteLink) => {
  try {
    await ElMessageBox.confirm(`确定撤下「${site.title}」？站点将回到分享者的私有状态。`, '撤下站点', {
      confirmButtonText: '撤下',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await offlineSite(site.id)
    ElMessage.success('已撤下')
    await Promise.all([loadSites(), loadStats()])
  } catch {
    /* 请求层已提示 */
  }
}

const openTagDialog = (tag?: SiteTag) => {
  editingTagId.value = tag ? tag.id : null
  tagForm.name = tag ? tag.name : ''
  tagForm.sort = tag ? tag.sort : 0
  tagDialogVisible.value = true
}

const startEditTag = (tag: SiteTag) => openTagDialog(tag)

const saveTag = async () => {
  const name = tagForm.name.trim()
  if (!name) {
    ElMessage.warning('请填写标签名')
    return
  }
  tagSaving.value = true
  try {
    if (editingTagId.value) await updateSiteTag(editingTagId.value, { name, sort: tagForm.sort })
    else await createSiteTag({ name, sort: tagForm.sort })
    tagDialogVisible.value = false
    await loadTags()
  } catch {
    /* 请求层已提示 */
  } finally {
    tagSaving.value = false
  }
}

const handleDeleteTag = async (tag: SiteTag) => {
  const hint = tag.siteCount
    ? `该标签已关联 ${tag.siteCount} 个站点，删除后这些站点将失去该标签。`
    : '删除后不可恢复。'
  try {
    await ElMessageBox.confirm(`${hint}确定删除「${tag.name}」？`, '删除标签', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteSiteTag(tag.id)
    ElMessage.success('已删除')
    await loadTags()
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(() => {
  void loadSites()
  // 审批弹窗要用标签库，进入页面就先拉一次
  void loadTags()
  void loadStats()
})

onUnmounted(() => {
  clearCache()
  if (keywordTimer) clearTimeout(keywordTimer)
  if (usernameTimer) clearTimeout(usernameTimer)
})
</script>

<style scoped lang="scss">
.admin-sites {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  h2 {
    margin: 0;
    font-size: 22px;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

.stat-row {
  display: flex;
  gap: 20px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;

  .num {
    font-size: 20px;
    font-weight: 600;
  }

  .label {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  :deep(.filter-date) {
    flex: none !important;
    width: 250px !important;
    max-width: 250px !important;
  }
}

.search {
  width: 240px;
}

.filter-item {
  width: 150px;
}

.filter-order {
  width: 110px;
}

.site-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.thumb-cell {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--ev-bg-tint, #f2f4f8);
}

.thumb-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted, #90a4bb);
}

.site-desc {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.site-title {
  font-weight: 600;
}

.site-url {
  font-size: 12px;
  color: var(--ev-primary, #2f7cf6);
  text-decoration: none;
  word-break: break-all;
}

.tag-chip {
  margin: 0 4px 2px 0;
}

.muted {
  color: var(--ev-text-muted, #90a4bb);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.approve-site {
  display: flex;
  gap: 12px;
  padding: 10px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--ev-bg-tint, #f7f8fa);
}

.approve-thumb {
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light, #f2f4f8);
}

.approve-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.approve-title {
  font-size: 15px;
  font-weight: 600;
}

.approve-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ev-text-muted, #90a4bb);
  /* 介绍可能很长，弹窗里给它独立滚动，别把整个弹窗撑爆 */
  max-height: 96px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.dialog-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ev-text-muted, #90a4bb);
}

.tag-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tag-picker-tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-search {
  width: 100%;
}

.tag-create {
  display: flex;
  gap: 8px;
}

.tag-create-input {
  flex: 1;
}

.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
  padding: 4px 2px;
}

.tag-option {
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid var(--ev-border-default);
  font-size: 13px;
  color: var(--ev-text-secondary);
  user-select: none;
  transition: all 0.15s var(--ev-ease-out);

  &:hover {
    border-color: var(--ev-primary);
    color: var(--ev-primary);
  }

  &.is-selected {
    background: var(--ev-primary);
    border-color: var(--ev-primary);
    color: #ffffff;
  }
}

.weight-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.weight-label {
  font-size: 13px;
  color: var(--ev-text-secondary);
  flex-shrink: 0;
}

.weight-input {
  width: 160px;
}

.weight-hint {
  font-size: 12px;
  color: var(--ev-text-muted, #90a4bb);
}
</style>


