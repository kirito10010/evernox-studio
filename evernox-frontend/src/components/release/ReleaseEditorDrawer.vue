<template>
  <el-drawer
    ref="drawerRef"
    v-model="visible"
    :with-header="false"
    size="820px"
    class="release-editor-drawer"
    :before-close="onBeforeClose"
  >
    <div class="editor-shell">
      <header class="editor-head">
        <div class="head-text">
          <div class="kicker">
            {{ isPublished ? 'EDIT RELEASE' : currentReleaseId ? 'EDIT DRAFT' : 'NEW RELEASE' }}
          </div>
          <h3>{{ isPublished ? '编辑发布' : currentReleaseId ? '编辑草稿' : '新建发布' }}</h3>
          <p>启动器通过 latest.yml 读取版本号、附件与 SHA-512 校验值</p>
        </div>
        <button type="button" class="close-btn" @click="requestClose">
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div v-loading="loading" class="editor-body">
        <section class="meta-card">
          <div class="field-row">
            <label class="field">
              <span class="field-label">版本 tag</span>
              <el-input
                v-model="form.tag"
                :disabled="isPublished"
                placeholder="v1.0.0"
                maxlength="60"
                @input="dirty = true"
              />

              <span class="field-tip">
                {{ isPublished ? '已发布版本不可修改 tag' : '支持 v1.0.0 / 1.0.0-beta.1；启动器读取时会去掉 v 前缀' }}
              </span>

            </label>
            <label class="field">
              <span class="field-label">发布标题</span>
              <el-input
                v-model="form.title"
                placeholder="Version 1.0.0"
                maxlength="200"
                @input="dirty = true"
              />

            </label>
          </div>

          <label class="field">
            <span class="field-label">发布说明</span>
            <el-input
              v-model="form.notes"
              type="textarea"
              :rows="6"
              maxlength="20000"
              show-word-limit
              :placeholder="notesPlaceholder"
              @input="dirty = true"
            />

          </label>
        </section>

        <section class="section">
          <div class="section-head">
            <h4>附件</h4>
            <span>latest.yml 由系统根据附件自动生成</span>
          </div>
          <ReleaseAssetUploader
            :key="releaseId ?? 'new'"
            v-model="assets"
            :project-id="projectId"
            @uploading-change="onUploadingChange"
          />

          <div v-if="!assets.length" class="asset-hint">
            建议上传 Setup-x.y.z.exe 与 Setup-x.y.z.exe.blockmap；单个文件 ≤ 2GB。
          </div>
        </section>

        <section class="section">
          <div class="section-head">
            <h4>发布设置</h4>
          </div>
          <div class="latest-setting">
            <div>
              <strong>发布后设为最新</strong>
              <p>latest.yml 会立即指向这个版本</p>
            </div>
            <el-switch v-model="latestAfterPublish" :disabled="isPublished" />
          </div>
          <div v-if="isPublished" class="published-note">
            已发布版本的修改会立即生效，建议只改标题/说明或补充附件。
          </div>
        </section>

        <section v-if="isPublished" class="section">
          <div class="section-head">
            <h4>latest.yml</h4>
            <el-button link type="primary" size="small" @click="toggleYaml">
              {{ yamlVisible ? '收起' : '预览' }}
            </el-button>
          </div>
          <pre v-if="yamlVisible" class="yaml-box">{{ yamlText || '生成中…' }}</pre>
          <div v-if="yamlVisible && yamlText" class="yaml-actions">
            <el-button size="small" @click="copyYaml">复制 YAML</el-button>
            <el-button size="small" @click="copyYamlUrl">复制 latest.yml 地址</el-button>
          </div>

        </section>
      </div>

      <footer class="editor-foot">
        <div class="foot-hint">
          <el-icon><InfoFilled /></el-icon>
          <span v-if="uploading">附件上传中，完成后才能保存</span>
          <span v-else>保存草稿后仍可继续上传附件</span>
        </div>
        <div class="foot-actions">
          <el-button @click="requestClose">取消</el-button>
          <el-button :disabled="uploading" :loading="saving" @click="save(false)">
            {{ isPublished ? '保存修改' : '保存草稿' }}
          </el-button>
          <el-button
            v-if="!isPublished"
            type="primary"
            :disabled="uploading"
            :loading="publishing"
            @click="save(true)"
          >
            发布
          </el-button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRelease,
  getReleaseDetail,
  getReleaseLatestYml,
  publishRelease,
  updateRelease,
} from '@/api/adminRelease'
import type { ReleaseAsset, ReleaseDetail } from '@/types/release'
import { useClipboard } from '@/composables/useClipboard'
import { isValidTag, releaseAbsoluteUrl, releaseLatestUrl } from '@/utils/release'

import ReleaseAssetUploader from './ReleaseAssetUploader.vue'

const props = defineProps<{
  modelValue: boolean
  projectId: number
  projectSlug: string
  releaseId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const { copy } = useClipboard()

const drawerRef = ref<{ close: () => void }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const uploading = ref(false)
const dirty = ref(false)
const hydrating = ref(false)

const currentReleaseId = ref<number | null>(null)
const release = ref<ReleaseDetail | null>(null)
const assets = ref<ReleaseAsset[]>([])
const latestAfterPublish = ref(true)

const form = reactive({
  tag: '',
  title: '',
  notes: '',
})

const notesPlaceholder = '【新增】******\n【修复】*********'


const yamlVisible = ref(false)
const yamlText = ref('')

const isPublished = computed(() => release.value?.status === 1)

watch(
  () => props.modelValue,
  (open) => {
    if (open) void init()
  },
  { immediate: true }
)

watch(
  assets,
  () => {
    if (!hydrating.value) dirty.value = true
  },
  { deep: true }
)

async function init() {
  hydrating.value = true
  dirty.value = false
  yamlVisible.value = false
  yamlText.value = ''
  assets.value = []
  release.value = null
  currentReleaseId.value = props.releaseId
  form.tag = ''
  form.title = ''
  form.notes = ''
  latestAfterPublish.value = true

  if (!props.releaseId) {
    await nextTick()
    hydrating.value = false
    return
  }

  loading.value = true
  try {
    const res = await getReleaseDetail(props.releaseId)
    const detail = res.data
    if (!detail) return
    release.value = detail
    currentReleaseId.value = detail.id
    form.tag = detail.tag
    form.title = detail.title
    form.notes = detail.notes || ''
    assets.value = detail.assets || []
    latestAfterPublish.value = detail.latest
    if (detail.status === 1) {
      await loadYaml()
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
    await nextTick()
    hydrating.value = false
    dirty.value = false
  }
}

function onUploadingChange(value: boolean) {
  uploading.value = value
}

function requestClose() {
  drawerRef.value?.close()
}

async function onBeforeClose(done: () => void) {
  if (uploading.value) {
    ElMessage.warning('附件正在上传，请等待完成或取消上传')
    return
  }
  if (!dirty.value) {
    done()
    return
  }
  try {
    await ElMessageBox.confirm('还有未保存的修改，确定关闭吗？', '关闭编辑', {
      type: 'warning',
      confirmButtonText: '放弃修改',
    })
    done()
  } catch {
    /* 留在抽屉 */
  }
}

function validate(publishAfter: boolean): boolean {
  if (!form.tag.trim() || !isValidTag(form.tag)) {
    ElMessage.warning('版本 tag 需符合 v1.2.3 或 1.2.3-beta.1')
    return false
  }
  if (!form.title.trim()) {
    ElMessage.warning('请填写发布标题')
    return false
  }
  if (publishAfter) {
    if (!assets.value.length) {
      ElMessage.warning('至少上传一个附件后才能发布')
      return false
    }
    const hasFile = assets.value.some(
      (asset) => !/\.(blockmap|ya?ml)$/i.test(asset.fileName)
    )
    if (!hasFile) {
      ElMessage.warning('至少上传一个安装包或分发文件后才能发布')
      return false
    }
  }
  return true
}

async function save(publishAfter: boolean) {
  if (!validate(publishAfter)) return

  saving.value = true
  publishing.value = publishAfter
  try {
    const payload = {
      projectId: props.projectId,
      tag: form.tag.trim(),
      title: form.title.trim(),
      notes: form.notes,
      assetIds: assets.value.map((asset) => asset.id),
    }

    let detail: ReleaseDetail | undefined
    if (currentReleaseId.value) {
      const res = await updateRelease(currentReleaseId.value, payload)
      detail = res.data
    } else {
      const res = await createRelease(payload)
      detail = res.data
      if (detail) {
        currentReleaseId.value = detail.id
      }
    }

    if (detail) {
      release.value = detail
      hydrating.value = true
      assets.value = detail.assets || assets.value
      await nextTick()
      hydrating.value = false
    }
    dirty.value = false
    emit('saved')


    if (detail && publishAfter) {
      try {
        const published = await publishRelease(detail.id, latestAfterPublish.value)
        release.value = published.data || detail
        dirty.value = false
        emit('saved')
        ElMessage.success('发布成功，latest.yml 已更新')
        visible.value = false
      } catch {
        ElMessage.warning('版本已保存为草稿，发布失败，请重试')
      }
    } else {
      ElMessage.success(isPublished.value ? '修改已保存' : '草稿已保存')
      visible.value = false
    }
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
    publishing.value = false
  }
}

async function loadYaml() {
  if (!currentReleaseId.value) return
  try {
    const res = await getReleaseLatestYml(currentReleaseId.value)
    yamlText.value = res.data || ''
  } catch {
    yamlText.value = ''
  }
}

async function toggleYaml() {
  yamlVisible.value = !yamlVisible.value
  if (yamlVisible.value && !yamlText.value) {
    await loadYaml()
  }
}

async function copyYaml() {
  await copy(yamlText.value, 'latest.yml 内容已复制')
}

async function copyYamlUrl() {
  if (!props.projectSlug) return
  await copy(releaseAbsoluteUrl(releaseLatestUrl(props.projectSlug)), 'latest.yml 地址已复制')
}

</script>

<style scoped lang="scss">
.editor-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ev-bg-dialog);
  color: var(--ev-text-primary);
}

.editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 26px 18px;
  border-bottom: 1px solid var(--ev-border-subtle);
  background:
    radial-gradient(120% 200% at 100% 0%, rgba(var(--ev-primary-rgb), 0.12), transparent 60%),
    transparent;

  .kicker {
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.18em;
    color: var(--ev-primary);
  }

  h3 {
    margin: 6px 0 4px;
    font-size: 22px;
    font-weight: 750;
    color: var(--ev-text-primary);
  }

  p {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

.close-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 50%;
  background: var(--ev-bg-surface);
  color: var(--ev-text-secondary);
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: var(--ev-primary);
    border-color: rgba(var(--ev-primary-rgb), 0.45);
  }
}

.editor-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 26px 22px;
}

.meta-card,
.section {
  padding: 16px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 14px;
  background: var(--ev-bg-surface);
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ev-text-secondary);
}

.field-tip {
  font-size: 11px;
  line-height: 1.45;
  color: var(--ev-text-muted);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;

  h4 {
    font-size: 13.5px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  span {
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

.asset-hint {
  margin-top: 8px;
  font-size: 11px;
  color: var(--ev-text-muted);
}

.latest-setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  strong {
    font-size: 13px;
    color: var(--ev-text-primary);
  }

  p {
    margin-top: 2px;
    font-size: 11px;
    color: var(--ev-text-muted);
  }
}

.published-note {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(230, 162, 60, 0.1);
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-color-warning);
}

.yaml-box {
  max-height: 240px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--ev-border-subtle);
  background: rgba(var(--ev-primary-rgb), 0.04);
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ev-text-secondary);
  white-space: pre;
}

.yaml-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}


.editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 26px;
  border-top: 1px solid var(--ev-border-subtle);
  background: var(--ev-bg-surface);

  .foot-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: var(--ev-text-muted);
  }

  .foot-actions {
    display: flex;
    gap: 8px;
  }
}

@media (max-width: 900px) {
  .editor-head,
  .editor-body,
  .editor-foot {
    padding-left: 16px;
    padding-right: 16px;
  }

  .field-row {
    grid-template-columns: 1fr;
  }

  .editor-foot {
    flex-direction: column;
    align-items: stretch;

    .foot-actions {
      .el-button {
        flex: 1;
      }
    }
  }
}
</style>

<style lang="scss">
.release-editor-drawer {
  .el-drawer__body {
    padding: 0;
    overflow: hidden;
  }
}

@media (max-width: 900px) {
  .release-editor-drawer {
    width: 100% !important;
  }
}
</style>
