<template>
  <div class="distribution-panel">
    <div class="panel-head">
      <div class="panel-title-wrap">
        <span class="panel-title">一键发包方案</span>
        <span class="panel-desc">保存常用发包规则，周记录页选择方案即可一键发放</span>
      </div>
      <el-button type="primary" :disabled="!organizationId" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新建方案</span>
      </el-button>
    </div>

    <el-table v-loading="loading" :data="plans" border stripe size="small">
      <el-table-column prop="name" label="方案名称" min-width="140" />
      <el-table-column label="排名依据" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">
            {{ OrgDistributionRankLabels[row.rankField as OrgDistributionRankField] || row.rankField }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="礼包队列" min-width="260">
        <template #default="{ row }">
          <div class="queue-chips">
            <span v-for="(item, idx) in (row as OrgDistributionPlan).items" :key="idx" class="queue-chip">
              {{ item.packageName ?? '已删除礼包' }}<b>×{{ item.quantity }}</b>
            </span>
            <span v-if="!row.items.length" class="muted">未配置</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="必发" width="80" align="center">
        <template #default="{ row }">
          <span class="forced-count">{{ row.forced.length }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as OrgDistributionPlan)">编辑</el-button>
          <el-button link type="danger" @click="removePlan(row as OrgDistributionPlan)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && plans.length === 0"
      description="暂无方案，点击右上角「新建方案」"
      :image-size="70"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑一键发包方案' : '新建一键发包方案'"
      width="720px"
      append-to-body
      class="distribution-dialog"
    >
      <div class="dialog-form">
        <div class="form-grid">
          <div class="field">
            <label>方案名称</label>
            <el-input v-model="form.name" maxlength="100" placeholder="如：本服主方案" />
          </div>
          <div class="field">
            <label>排名依据</label>
            <el-select v-model="form.rankField" style="width: 100%">
              <el-option
                v-for="(label, value) in OrgDistributionRankLabels"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </div>
        </div>
        <p class="tip">忍战次数为 0 或未录入的成员不参与排名，也不会被必发。</p>

        <div class="section">
          <div class="section-head">
            <span class="section-title">礼包队列</span>
            <span class="section-desc">共 {{ totalQuantity }} 个名额，按顺序发放</span>
          </div>

          <div v-if="form.items.length" class="item-list">
            <div v-for="(item, idx) in form.items" :key="idx" class="item-row">
              <span class="item-index">{{ idx + 1 }}</span>
              <el-select v-model="item.packageId" placeholder="选择礼包" class="item-package">
                <el-option v-for="p in packages" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :max="999"
                :precision="0"
                class="item-qty"
              />
              <span class="qty-unit">个</span>
              <el-button
                text
                type="danger"
                class="remove-btn"
                :disabled="form.items.length === 1"
                @click="removeItem(idx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-else class="empty-block">还没有礼包，点击下方按钮添加</div>

          <el-button class="add-block" :disabled="!packages.length" @click="addItem">
            <el-icon><Plus /></el-icon>
            <span>添加礼包</span>
          </el-button>
          <p v-if="!packages.length" class="tip">当前组织还没有礼包，请先在「奖励礼包」中添加</p>
        </div>

        <div class="section">
          <div class="section-head">
            <span class="section-title">必发玩家</span>
            <span class="section-desc">每名必发玩家占用对应礼包 1 个名额</span>
          </div>

          <div class="forced-add">
            <el-select
              v-model="forcedDraft.memberId"
              filterable
              placeholder="搜索组织成员"
              class="forced-member"
            >
              <el-option v-for="m in activeMembers" :key="m.id" :label="m.name" :value="m.id" />
            </el-select>
            <el-select v-model="forcedDraft.packageId" placeholder="选择礼包" class="forced-package">
              <el-option v-for="p in configuredPackages" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-button type="primary" plain @click="addForced">
              <el-icon><Plus /></el-icon>
              <span>添加</span>
            </el-button>
          </div>

          <div v-if="forcedRows.length" class="forced-list">
            <div v-for="(f, idx) in forcedRows" :key="f.memberId" class="forced-item">
              <el-icon class="forced-icon"><User /></el-icon>
              <span class="forced-name">{{ f.memberName }}</span>
              <el-tag size="small" effect="plain">{{ f.packageName }}</el-tag>
              <el-button text type="danger" class="forced-remove" @click="form.forced.splice(idx, 1)">
                移除
              </el-button>
            </div>
          </div>
          <p v-else class="tip">未设置必发玩家</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createOrgDistributionPlan,
  deleteOrgDistributionPlan,
  getOrgDistributionPlans,
  updateOrgDistributionPlan,
} from '@/api/org'
import type {
  OrgDistributionPlan,
  OrgDistributionPlanRequest,
  OrgDistributionRankField,
  OrgMember,
  OrgRewardPackage,
} from '@/types/org'
import { OrgDistributionRankLabels } from '@/types/org'

const props = defineProps<{
  organizationId: number | null
  packages: OrgRewardPackage[]
  members: OrgMember[]
}>()

const emit = defineEmits<{ (e: 'updated', plans: OrgDistributionPlan[]): void }>()

const plans = ref<OrgDistributionPlan[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

interface EditableItem {
  packageId: number | null
  quantity: number
}

interface EditableForced {
  memberId: number
  packageId: number
}

const form = reactive<{
  name: string
  rankField: OrgDistributionRankField
  items: EditableItem[]
  forced: EditableForced[]
}>({
  name: '',
  rankField: 'totalPoints',
  items: [],
  forced: [],
})

const forcedDraft = reactive<{ memberId: number | null; packageId: number | null }>({
  memberId: null,
  packageId: null,
})

const activeMembers = computed(() =>
  props.members.filter((m) => m.organizationId === props.organizationId && m.status === 1)
)

const configuredPackages = computed(() => {
  const ids = new Set(form.items.map((i) => i.packageId).filter((v): v is number => v != null))
  return props.packages.filter((p) => ids.has(p.id))
})

const totalQuantity = computed(() =>
  form.items.reduce((sum, i) => sum + (i.quantity || 0), 0)
)

const forcedRows = computed(() =>
  form.forced.map((f) => ({
    ...f,
    memberName: props.members.find((m) => m.id === f.memberId)?.name ?? '未知成员',
    packageName: props.packages.find((p) => p.id === f.packageId)?.name ?? '未知礼包',
  }))
)

const loadPlans = async () => {
  if (!props.organizationId) {
    plans.value = []
    emit('updated', [])
    return
  }
  loading.value = true
  try {
    const res = await getOrgDistributionPlans(props.organizationId)
    plans.value = res.data ?? []
    emit('updated', plans.value)
  } catch {
    /* 请求层已提示 */
  } finally {
    loading.value = false
  }
}

watch(() => props.organizationId, loadPlans, { immediate: true })

// 礼包改名/删除后，父组件可主动刷新方案列表
defineExpose({ reload: loadPlans })

const resetForm = () => {
  form.name = ''
  form.rankField = 'totalPoints'
  form.items = []
  form.forced = []
  forcedDraft.memberId = null
  forcedDraft.packageId = null
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  if (props.packages.length) {
    form.items = [{ packageId: props.packages[0].id, quantity: 1 }]
  }
  dialogVisible.value = true
}

const openEdit = (plan: OrgDistributionPlan) => {
  editingId.value = plan.id
  form.name = plan.name
  form.rankField = plan.rankField
  const packageIds = new Set(props.packages.map((p) => p.id))
  form.items = plan.items
    .filter((i) => packageIds.has(i.packageId))
    .map((i) => ({ packageId: i.packageId, quantity: i.quantity }))
  form.forced = plan.forced
    .filter((f) => packageIds.has(f.packageId))
    .map((f) => ({ memberId: f.memberId, packageId: f.packageId }))
  forcedDraft.memberId = null
  forcedDraft.packageId = null
  if (!form.items.length && props.packages.length) {
    form.items = [{ packageId: props.packages[0].id, quantity: 1 }]
  }
  dialogVisible.value = true
}

const addItem = () => {
  const used = new Set(form.items.map((i) => i.packageId))
  const next = props.packages.find((p) => !used.has(p.id))
  form.items.push({ packageId: next?.id ?? null, quantity: 1 })
}

const removeItem = (idx: number) => {
  const removed = form.items[idx]
  form.items.splice(idx, 1)
  if (!removed || removed.packageId == null) return
  // 礼包从队列移除后，指向它的必发设置一并清理
  const stillUsed = form.items.some((i) => i.packageId === removed.packageId)
  if (!stillUsed) {
    form.forced = form.forced.filter((f) => f.packageId !== removed.packageId)
  }
}

const addForced = () => {
  if (!forcedDraft.memberId || !forcedDraft.packageId) {
    ElMessage.warning('请选择玩家和必发礼包')
    return
  }
  if (form.forced.some((f) => f.memberId === forcedDraft.memberId)) {
    ElMessage.warning('该玩家已在必发列表中')
    return
  }
  const item = form.items.find((i) => i.packageId === forcedDraft.packageId)
  if (!item) {
    ElMessage.warning('必发礼包必须先在礼包队列中配置')
    return
  }
  const used = form.forced.filter((f) => f.packageId === forcedDraft.packageId).length
  if (used >= item.quantity) {
    ElMessage.warning('该礼包的必发人数已达到配置数量')
    return
  }
  form.forced.push({ memberId: forcedDraft.memberId, packageId: forcedDraft.packageId })
  forcedDraft.memberId = null
}

const save = async () => {
  if (!props.organizationId) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入方案名称')
    return
  }
  const items = form.items
    .filter((i): i is { packageId: number; quantity: number } => i.packageId != null)
    .map((i) => ({ packageId: i.packageId, quantity: i.quantity }))
  if (!items.length) {
    ElMessage.warning('请至少添加一个礼包')
    return
  }
  if (items.some((i) => !i.quantity || i.quantity < 1)) {
    ElMessage.warning('礼包数量必须大于 0')
    return
  }
  if (new Set(items.map((i) => i.packageId)).size !== items.length) {
    ElMessage.warning('同一个礼包不能重复添加')
    return
  }

  const quantityByPackage = new Map(items.map((i) => [i.packageId, i.quantity]))
  const forced = form.forced.map((f) => ({ memberId: f.memberId, packageId: f.packageId }))
  for (const f of forced) {
    if (!quantityByPackage.has(f.packageId)) {
      ElMessage.warning('必发礼包必须先在礼包队列中配置')
      return
    }
  }
  const forcedCount = new Map<number, number>()
  forced.forEach((f) => forcedCount.set(f.packageId, (forcedCount.get(f.packageId) ?? 0) + 1))
  for (const [packageId, count] of forcedCount) {
    if (count > (quantityByPackage.get(packageId) ?? 0)) {
      ElMessage.warning('必发人数不能超过该礼包的配置数量')
      return
    }
  }

  const data: OrgDistributionPlanRequest = {
    name: form.name.trim(),
    rankField: form.rankField,
    items,
    forced,
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateOrgDistributionPlan(editingId.value, data)
    } else {
      await createOrgDistributionPlan(props.organizationId, data)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadPlans()
  } finally {
    saving.value = false
  }
}

const removePlan = async (plan: OrgDistributionPlan) => {
  try {
    await ElMessageBox.confirm(`确定删除方案「${plan.name}」吗？`, '删除方案', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deleteOrgDistributionPlan(plan.id)
  ElMessage.success('已删除')
  await loadPlans()
}
</script>

<!-- 弹窗外壳（非 scoped：类名只在本组件使用） -->
<style lang="scss">
.el-dialog.distribution-dialog {
  border-radius: 18px !important;
  overflow: hidden;

  .el-dialog__header {
    margin-right: 0;
    padding: 20px 24px 0;
  }

  .el-dialog__title {
    font-size: 16px;
    font-weight: 700;
  }

  .el-dialog__body {
    padding: 16px 24px 4px !important;
    max-height: 64vh;
    overflow-y: auto;
  }

  .el-dialog__footer {
    padding: 12px 24px 18px;
    border-top: 1px solid var(--ev-border-subtle, rgba(144, 164, 187, 0.24));
  }
}
</style>

<style scoped lang="scss">
.distribution-panel {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px dashed var(--ev-border-subtle, rgba(144, 164, 187, 0.3));

  .panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
  }

  .panel-title-wrap {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .panel-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--ev-text-primary, #1f2d3d);
  }

  .panel-desc {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }

  .queue-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .queue-chip {
    padding: 1px 8px;
    border-radius: 6px;
    border: 1px solid var(--ev-border-subtle, rgba(144, 164, 187, 0.3));
    background: var(--ev-bg-tint, rgba(47, 124, 246, 0.06));
    font-size: 12px;
    color: var(--ev-text-secondary, #4c6b8a);

    b {
      margin-left: 2px;
      color: var(--ev-primary, #2f7cf6);
    }
  }

  .forced-count {
    font-weight: 600;
    color: var(--ev-primary, #2f7cf6);
  }

  .muted {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }
}

/* ---------- 弹窗内容 ---------- */
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .form-grid {
    display: grid;
    grid-template-columns: 1fr 220px;
    gap: 16px;
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;

    label {
      font-size: 13px;
      color: var(--ev-text-secondary, #4c6b8a);
    }
  }

  .tip {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--ev-text-muted, #90a4bb);
  }

  .section {
    margin-top: 18px;
    padding: 16px;
    border: 1px solid var(--ev-border-subtle, rgba(144, 164, 187, 0.24));
    border-radius: 12px;
    background: var(--ev-bg-tint, rgba(47, 124, 246, 0.03));
  }

  .section-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 12px;
  }

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--ev-text-primary, #1f2d3d);
  }

  .section-desc {
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }

  .item-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .item-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border-radius: 10px;
    background: var(--ev-bg-glass-strong, rgba(255, 255, 255, 0.7));
    border: 1px solid var(--ev-border-subtle, rgba(144, 164, 187, 0.2));
  }

  .item-index {
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--ev-primary, #2f7cf6);
    color: #fff;
    font-size: 11px;
    font-weight: 600;
  }

  .item-package {
    flex: 1;
    min-width: 0;
  }

  .item-qty {
    width: 110px;
    flex-shrink: 0;
  }

  .qty-unit {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }

  .remove-btn {
    flex-shrink: 0;
  }

  .empty-block {
    padding: 14px;
    border: 1px dashed var(--ev-border-default, rgba(144, 164, 187, 0.4));
    border-radius: 10px;
    text-align: center;
    font-size: 12px;
    color: var(--ev-text-muted, #90a4bb);
  }

  /* 整行虚线按钮：明确表达「可点击添加」 */
  .add-block {
    width: 100%;
    height: 38px;
    margin-top: 10px;
    border: 1px dashed var(--ev-border-hover, rgba(47, 124, 246, 0.5));
    background: transparent;
    color: var(--ev-primary, #2f7cf6);
    font-size: 13px;
    gap: 4px;

    &:hover:not(.is-disabled) {
      background: var(--ev-bg-tint-strong, rgba(47, 124, 246, 0.1));
      border-color: var(--ev-primary, #2f7cf6);
    }
  }

  .forced-add {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 12px;
  }

  .forced-member,
  .forced-package {
    width: 200px;
  }

  .forced-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .forced-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 8px;
    background: var(--ev-bg-glass-strong, rgba(255, 255, 255, 0.7));
    border: 1px solid var(--ev-border-subtle, rgba(144, 164, 187, 0.2));
  }

  .forced-icon {
    color: var(--ev-primary, #2f7cf6);
  }

  .forced-name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--ev-text-primary, #1f2d3d);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .forced-remove {
    flex-shrink: 0;
  }
}

@media (max-width: 640px) {
  .dialog-form {
    .form-grid {
      grid-template-columns: 1fr;
    }

    .forced-member,
    .forced-package {
      width: 100%;
    }
  }
}
</style>
