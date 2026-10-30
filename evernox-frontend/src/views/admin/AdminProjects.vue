<template>
  <div class="admin-projects">
    <header class="projects-head">
      <div class="head-main">
        <div class="head-title">
          <h1><el-icon><FolderOpened /></el-icon> 项目管理</h1>
          <p>每个项目独享版本、附件与更新清单</p>
        </div>
        <div class="head-stats">
          <span><strong>{{ projects.length }}</strong> 项目</span>
          <span><strong>{{ totalReleases }}</strong> 版本</span>
          <span><strong>{{ totalPublished }}</strong> 已发布</span>
          <span><strong>{{ formatBytes(totalStorage) }}</strong> 占用</span>
          <span><strong>{{ totalDownloads }}</strong> 下载</span>
        </div>
      </div>
      <div class="head-actions">
        <el-button circle text :loading="loading" title="刷新" @click="load">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建项目
        </el-button>
      </div>
    </header>

    <div class="project-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索项目名称 / slug / 描述"
        clearable
        :prefix-icon="Search"
        class="project-search"
      />
      <span class="project-total">共 {{ filteredProjects.length }} 个项目</span>
    </div>

    <div v-loading="loading" class="project-grid">
      <article
        v-for="p in filteredProjects"
        :key="p.id"
        class="project-card"
        @click="enterProject(p)"
      >
        <div class="project-top">
          <div class="project-mark">{{ p.name.slice(0, 1) }}</div>
          <div class="project-title">
            <h3 :title="p.name">{{ p.name }}</h3>
            <code>{{ p.slug }}</code>
          </div>
          <span class="more-wrap" @click.stop>
            <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, p)">
              <el-button circle text class="more-btn">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">
                    <el-icon><Edit /></el-icon> 编辑项目
                  </el-dropdown-item>
                  <el-dropdown-item command="copy">
                    <el-icon><Link /></el-icon> 复制更新地址
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <span class="danger-item"><el-icon><Delete /></el-icon> 删除项目</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>

        <p v-if="p.description" class="project-desc" :title="p.description">{{ p.description }}</p>

        <div class="project-metrics">
          <span><strong>{{ p.releaseCount }}</strong> 版本</span>
          <span v-if="p.latestTag">最新 <code>{{ p.latestTag }}</code></span>
          <span>{{ formatBytes(p.storageBytes) }}</span>
          <span><strong>{{ p.downloadTotal }}</strong> 下载</span>
        </div>

        <div class="project-foot">
          <span class="project-updated">{{ formatDateTime(p.updatedAt) }}</span>
          <el-button size="small" type="primary" @click.stop="enterProject(p)">管理版本</el-button>
        </div>
      </article>

      <button
        v-if="!projects.length"
        type="button"
        class="project-card create-card"
        @click="openCreate"
      >
        <el-icon class="create-icon"><Plus /></el-icon>
        <span class="create-title">新建项目</span>
        <span class="create-tip">为一个新项目创建独立的发布空间</span>
      </button>

      <div v-if="projects.length && !filteredProjects.length" class="no-result">
        没有匹配的项目
      </div>

    </div>

    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑项目' : '新建项目'"
      width="520px"
      class="release-project-dialog"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" maxlength="80" placeholder="如：火影OL 启动器" />
        </el-form-item>
        <el-form-item label="slug（公开地址标识）" prop="slug">
          <el-input
            v-model="form.slug"
            maxlength="40"
            :disabled="!!editingId"
            placeholder="naruto-launcher"
          >
            <template #prefix>/release/public/</template>
          </el-input>
          <div class="field-tip">
            只能使用小写字母、数字和短横线；创建后不可修改。
          </div>
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="这个项目发布什么内容"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="99999" />
          <span class="field-tip inline">数值越大越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editingId ? '保存' : '创建项目' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  createReleaseProject,
  deleteReleaseProject,
  getReleaseProjects,
  updateReleaseProject,
} from '@/api/adminRelease'
import type { ReleaseProject } from '@/types/release'
import { formatBytes } from '@/utils/image'
import { formatDateTime } from '@/utils/format'
import { useClipboard } from '@/composables/useClipboard'
import { isValidSlug, releaseAbsoluteUrl, releaseBaseUrl } from '@/utils/release'

const router = useRouter()
const { copy } = useClipboard()

const loading = ref(false)
const saving = ref(false)
const projects = ref<ReleaseProject[]>([])
const keyword = ref('')

const filteredProjects = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return projects.value
  return projects.value.filter((p) =>
    [p.name, p.slug, p.description || ''].some((v) => v.toLowerCase().includes(kw))
  )
})

const totalReleases = computed(() => projects.value.reduce((sum, p) => sum + p.releaseCount, 0))
const totalPublished = computed(() => projects.value.reduce((sum, p) => sum + p.publishedCount, 0))
const totalStorage = computed(() => projects.value.reduce((sum, p) => sum + p.storageBytes, 0))
const totalDownloads = computed(() => projects.value.reduce((sum, p) => sum + p.downloadTotal, 0))

async function load() {
  loading.value = true
  try {
    const res = await getReleaseProjects()
    projects.value = res.data || []
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

function enterProject(p: ReleaseProject) {
  router.push(`/workspace/projects/${p.id}/releases`)

}

async function copyBase(p: ReleaseProject) {
  await copy(releaseAbsoluteUrl(releaseBaseUrl(p.slug)), '更新地址已复制')
}

function onCommand(command: string, p: ReleaseProject) {
  if (command === 'edit') openEdit(p)
  else if (command === 'copy') copyBase(p)
  else if (command === 'delete') removeProject(p)
}

// ==================== 新建 / 编辑 ====================

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  slug: '',
  description: '',
  sort: 0,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  slug: [
    { required: true, message: '请输入 slug', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidSlug(String(value || ''))) {
          callback(new Error('slug 只能使用小写字母、数字和短横线，且不能是保留字'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.slug = ''
  form.description = ''
  form.sort = 0
  editorVisible.value = true
}

function openEdit(p: ReleaseProject) {
  editingId.value = p.id
  form.name = p.name
  form.slug = p.slug
  form.description = p.description || ''
  form.sort = p.sort
  editorVisible.value = true
}

function resetForm() {
  editingId.value = null
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      slug: form.slug.trim(),
      description: form.description.trim(),
      sort: form.sort,
    }
    if (editingId.value) {
      await updateReleaseProject(editingId.value, payload)
      ElMessage.success('项目已更新')
    } else {
      await createReleaseProject(payload)
      ElMessage.success('项目已创建')
    }
    editorVisible.value = false
    await load()
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}

async function removeProject(p: ReleaseProject) {
  try {
    const { value } = await ElMessageBox.prompt(
      `将删除「${p.name}」下的全部版本、附件与磁盘文件，且不可恢复。请输入 slug 确认：`,
      '删除项目',
      {
        inputPlaceholder: p.slug,
        inputValidator: (v) => v === p.slug || 'slug 不匹配',
        confirmButtonText: '删除项目',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await deleteReleaseProject(p.id, value)
    ElMessage.success('项目已删除')
    await load()
  } catch {
    /* 用户取消或请求层已提示 */
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.admin-projects {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.projects-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 24px;
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

.head-title {
  h1 {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 20px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    color: var(--ev-text-secondary);
  }
}

.head-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 12px;
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

.project-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 2px;

  .project-search {
    max-width: 340px;
  }

  .project-total {
    margin-left: auto;
    font-size: 11.5px;
    color: var(--ev-text-muted);
  }
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 14px;
  min-height: 140px;
}

.project-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--ev-border-subtle);
  border-top-color: var(--ev-border-gloss);
  border-radius: 14px;
  background: var(--ev-bg-glass);
  backdrop-filter: var(--ev-blur-md);
  -webkit-backdrop-filter: var(--ev-blur-md);
  box-shadow: var(--ev-shadow-card), var(--ev-inset-gloss);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s var(--ev-ease-out), border-color 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(var(--ev-primary-rgb), 0.4);
  }
}

.project-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--ev-primary), var(--ev-aqua));
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  box-shadow: 0 5px 12px rgba(var(--ev-primary-rgb), 0.24);
}

.project-title {
  min-width: 0;
  flex: 1;

  h3 {
    font-size: 14.5px;
    font-weight: 700;
    color: var(--ev-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  code {
    font-size: 10.5px;
    color: var(--ev-text-muted);
  }
}

.more-wrap {
  flex-shrink: 0;
}

.more-btn {
  flex-shrink: 0;
}

.danger-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-danger);
}

.project-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11.5px;
  color: var(--ev-text-secondary);
}

.project-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  font-size: 11px;
  color: var(--ev-text-muted);

  strong {
    color: var(--ev-text-primary);
    font-size: 12.5px;
  }

  code {
    color: var(--ev-primary);
    font-weight: 600;
  }
}

.project-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--ev-border-subtle);
}

.project-updated {
  font-size: 10.5px;
  color: var(--ev-text-muted);
}

.create-card {
  grid-column: 1 / -1;
  align-items: center;
  justify-content: center;
  min-height: 150px;
  border-style: dashed;
  background: transparent;
  color: var(--ev-text-muted);
  gap: 4px;

  &:hover {
    color: var(--ev-primary);
    border-color: rgba(var(--ev-primary-rgb), 0.45);
    background: rgba(var(--ev-primary-rgb), 0.04);
  }

  .create-icon {
    font-size: 24px;
  }

  .create-title {
    font-size: 13.5px;
    font-weight: 700;
  }

  .create-tip {
    font-size: 11px;
  }
}

.no-result {
  grid-column: 1 / -1;
  padding: 40px 0;
  text-align: center;
  font-size: 12px;
  color: var(--ev-text-muted);
}


.field-tip {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ev-text-muted);

  &.inline {
    margin: 0 0 0 10px;
  }
}

@media (max-width: 900px) {
  .projects-head {
    padding: 16px 18px;
    flex-direction: column;
  }

  .head-actions {
    width: 100%;

    .el-button {
      flex: 1;
    }
  }

  .project-grid {
    grid-template-columns: 1fr;
  }

  .project-toolbar {
    flex-wrap: wrap;

    .project-search {
      max-width: none;
      width: 100%;
    }

    .project-total {
      margin-left: 0;
    }
  }
}
</style>

<style lang="scss">
.release-project-dialog {
  max-width: calc(100vw - 24px);
}
</style>
