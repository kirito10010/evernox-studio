<template>
  <div class="admin-org">
    <el-tabs v-model="activeTab" class="org-tabs">
      <!-- ==================== 成员管理 ==================== -->
      <el-tab-pane label="成员管理" name="members">
        <div class="toolbar">
          <el-button type="primary" @click="openOrgDialog()">添加组织</el-button>
          <el-button @click="orgManageVisible = true">管理组织</el-button>
          <el-button type="primary" @click="openMemberDialog()">添加成员</el-button>
          <span class="toolbar-label">导入到组织：</span>
          <el-select v-model="memberImportOrgId" placeholder="选择组织" style="width: 160px">
            <el-option v-for="o in organizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-upload
            :auto-upload="false"
            accept=".xlsx"
            :show-file-list="false"
            :on-change="onMemberFileChange"
          >
            <el-button :disabled="!memberImportOrgId">选择 Excel</el-button>
          </el-upload>
          <el-button
            type="primary"
            :disabled="!memberImportFile || !memberImportOrgId"
            :loading="importingMembers"
            @click="doMemberImport"
          >
            导入成员
          </el-button>
          <el-input v-model="memberKeyword" placeholder="搜索玩家名" clearable style="width: 180px" />
        </div>
        <el-table :data="filteredMembers" border stripe>
          <el-table-column prop="name" label="玩家名" min-width="130" />
          <el-table-column prop="organizationName" label="所属组织" min-width="120" />
          <el-table-column prop="position" label="职务" min-width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '在组织' : '已离开' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openMemberDialog(row as OrgMember)">编辑</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                @click="toggleStatus(row as OrgMember)"
              >
                {{ row.status === 1 ? '离开' : '恢复' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 积分换算比 ==================== -->
      <el-tab-pane label="积分换算比" name="config">
        <div class="toolbar">
          <span class="toolbar-label">选择组织：</span>
          <el-select v-model="selectedOrgId" placeholder="选择组织" style="width: 200px">
            <el-option v-for="o in organizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </div>
        <div v-if="selectedOrgId" class="config-panel">
          <div class="config-item" v-for="item in configRows" :key="item.label">
            <span class="config-label">{{ item.label }}</span>
            <el-input-number v-model="config[item.pointsKey]" :min="0" :precision="5" :controls="false" class="points-input" />
            <span class="config-eq">积分</span>
            <span class="config-eq">启用</span>
            <el-switch v-model="config[item.enabledKey]" :active-value="1" :inactive-value="0" />
            <span class="config-eq">显示列</span>
            <el-switch v-model="config[item.visibleKey]" :active-value="1" :inactive-value="0" />
          </div>
          <div class="config-item">
            <span class="config-label">未领礼包积分调整</span>
            <el-input-number v-model="config.noPackageAdjustment" :min="-999999" :max="999999" :precision="5" :controls="false" class="points-input" />
            <span class="config-eq">积分（未领礼包下周继承时额外加减，可为负）</span>
          </div>
          <el-button type="primary" :loading="savingConfig" @click="saveConfig">保存换算比</el-button>
        </div>
        <el-empty v-else description="请先选择组织" />
      </el-tab-pane>

      <!-- ==================== 奖励礼包 ==================== -->
      <el-tab-pane label="奖励礼包" name="packages">
        <div class="toolbar">
          <span class="toolbar-label">选择组织：</span>
          <el-select v-model="selectedOrgId" placeholder="选择组织" style="width: 200px">
            <el-option v-for="o in organizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-button type="primary" :disabled="!selectedOrgId" @click="openPackageDialog()">添加礼包</el-button>
        </div>
        <el-table :data="packages" border stripe>
          <el-table-column prop="name" label="礼包名称" min-width="160" />
          <el-table-column label="扣除比例" min-width="120">
            <template #default="{ row }">{{ (row.deductionRatio * 100).toFixed(0) }}%</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPackageDialog(row as OrgRewardPackage)">编辑</el-button>
              <el-button link type="danger" @click="deletePackage(row as OrgRewardPackage)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 周记录 ==================== -->
      <el-tab-pane label="周记录" name="records">
        <div class="toolbar records-toolbar">
          <span class="toolbar-label">组织：</span>
          <el-select v-model="selectedOrgId" placeholder="选择组织" style="width: 180px">
            <el-option v-for="o in organizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select
            v-model="selectedWeek"
            placeholder="选择周（周日）"
            clearable
            style="width: 180px"
            @change="loadRecords"
          >
            <el-option v-for="w in weeks" :key="w" :label="w" :value="w" />
          </el-select>
          <el-date-picker
            v-model="generateDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="生成周（默认本周周日）"
            clearable
            style="width: 190px"
          />
          <el-button type="primary" :disabled="!selectedOrgId" @click="generate">一键生成</el-button>
          <el-upload
            :auto-upload="false"
            accept=".xlsx"
            :show-file-list="false"
            :on-change="onFileChange"
          >
            <el-button :disabled="!selectedOrgId">选择 Excel</el-button>
          </el-upload>
          <el-button type="success" :disabled="!uploadFile || !selectedOrgId" :loading="importing" @click="doImport">
            导入数据
          </el-button>
          <el-button type="warning" :disabled="!selectedOrgId || !selectedWeek" @click="calculate">计算积分</el-button>
          <el-button type="danger" :disabled="!selectedOrgId || !selectedWeek" @click="deleteWeek">删除本周</el-button>
          <el-input v-model="recordKeyword" placeholder="搜索玩家名" clearable style="width: 180px" />
        </div>

        <el-table :data="filteredRecords" border stripe :default-sort="{ prop: 'totalPoints', order: 'descending' }">
          <el-table-column prop="memberName" label="玩家名" min-width="90" fixed="left" />
          <el-table-column prop="position" label="职务" min-width="90" />
          <el-table-column prop="ninjaBattleCount" v-if="config.ninjaBattleVisible === 1" label="忍战次数" min-width="90" sortable />
          <el-table-column prop="totalPower" v-if="config.totalPowerVisible === 1" label="总战力" min-width="100" sortable />
          <el-table-column prop="powerIncrease" v-if="config.powerIncreaseVisible === 1" label="战力增幅" min-width="90" sortable />
          <el-table-column prop="copperContribution" v-if="config.copperVisible === 1" label="铜币" min-width="80" sortable />
          <el-table-column prop="beastSacrifice" v-if="config.beastVisible === 1" label="通灵兽" min-width="80" sortable />
          <el-table-column prop="renegadeCount" v-if="config.renegadeVisible === 1" label="叛忍" min-width="70" sortable />
          <el-table-column v-if="config.renegadeLeaderVisible === 1" label="车头" min-width="70">
            <template #default="{ row }">{{ row.isRenegadeLeader === 1 ? '是' : '' }}</template>
          </el-table-column>
          <el-table-column label="上周剩余" min-width="100" sortable prop="lastWeekPoints">
            <template #default="{ row }">{{ fmt(row.lastWeekPoints) }}</template>
          </el-table-column>
          <el-table-column label="本周积分" min-width="100" sortable prop="thisWeekPoints">
            <template #default="{ row }">{{ fmt(row.thisWeekPoints) }}</template>
          </el-table-column>
          <el-table-column label="总积分" min-width="100" sortable prop="totalPoints">
            <template #default="{ row }">{{ fmt(row.totalPoints) }}</template>
          </el-table-column>
          <el-table-column label="奖励礼包" width="170" fixed="right">
            <template #default="{ row }">
              <el-select
                :model-value="row.rewardPackageId"
                placeholder="选择礼包"
                clearable
                size="small"
                @change="(val: number) => handleSetPackage(row as OrgWeekRecord, val)"
              >
                <el-option v-for="p in packages" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="扣除后积分" width="110" fixed="right" sortable prop="pointsAfterDeduction">
            <template #default="{ row }">{{ fmt(row.pointsAfterDeduction) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRecordEdit(row as OrgWeekRecord)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 加入审批 ==================== -->
      <el-tab-pane label="加入审批" name="applications">
        <el-table :data="applications" border stripe v-loading="loadingApplications">
          <el-table-column prop="organizationName" label="组织" min-width="140" />
          <el-table-column prop="username" label="申请人" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="200" />
          <el-table-column label="申请时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.appliedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="approveApplication(row as OrgMembershipApplication)">通过</el-button>
              <el-button link type="danger" @click="rejectApplication(row as OrgMembershipApplication)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loadingApplications && applications.length === 0" description="暂无待审批申请" />
      </el-tab-pane>
    </el-tabs>

    <!-- 成员编辑弹窗 -->
    <el-dialog v-model="memberDialogVisible" :title="editingMemberId ? '编辑成员' : '添加成员'" width="440px">
      <el-form label-width="80px">
        <el-form-item label="所属组织" required>
          <el-select v-model="memberForm.organizationId" placeholder="请选择组织" style="width: 100%">
            <el-option v-for="o in organizations" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="玩家名" required>
          <el-input v-model="memberForm.name" placeholder="请输入玩家名" />
        </el-form-item>
        <el-form-item label="职务">
          <el-input v-model="memberForm.position" placeholder="如：成员 / 精英 / 暗部" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMember" @click="saveMember">保存</el-button>
      </template>
    </el-dialog>

    <!-- 组织编辑弹窗 -->
    <el-dialog v-model="orgDialogVisible" :title="editingOrgId ? '编辑组织' : '添加组织'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="组织名称" required>
          <el-input v-model="orgForm.name" placeholder="请输入组织名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingOrg" @click="saveOrg">保存</el-button>
      </template>
    </el-dialog>

    <!-- 组织管理弹窗 -->
    <el-dialog v-model="orgManageVisible" title="管理组织" width="520px">
      <el-table :data="organizations" border stripe>
        <el-table-column prop="name" label="组织名称" min-width="160" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openOrgDialog(row as OrgOrganization)">编辑</el-button>
            <el-button link type="danger" @click="deleteOrg(row as OrgOrganization)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 礼包编辑弹窗 -->
    <el-dialog v-model="packageDialogVisible" :title="editingPackageId ? '编辑礼包' : '添加礼包'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="礼包名称" required>
          <el-input v-model="packageForm.name" placeholder="请输入礼包名称" />
        </el-form-item>
        <el-form-item label="扣除比例" required>
          <el-input-number v-model="packageForm.deductionRatio" :min="0" :max="100" :precision="0" style="width: 200px" />
          <span style="margin-left: 8px">%（扣掉总积分的百分比）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPackage" @click="savePackage">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="520px">
      <div class="import-report">
        <div class="import-block">
          <div class="import-title import-success">成功导入（{{ importResult.importedNames.length }}）</div>
          <div class="import-names">{{ importResult.importedNames.join('、') || '无' }}</div>
        </div>
        <div class="import-block">
          <div class="import-title import-danger">未导入（{{ importResult.unmatchedNames.length }}）</div>
          <div class="import-names">{{ importResult.unmatchedNames.join('、') || '无' }}</div>
        </div>
        <div class="import-block">
          <div class="import-title import-warning">仍为空数据（{{ importResult.emptyNames.length }}）</div>
          <div class="import-names">{{ importResult.emptyNames.join('、') || '无' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="importResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 成员导入结果弹窗 -->
    <el-dialog v-model="memberImportResultVisible" title="成员导入结果" width="520px">
      <div class="import-report">
        <div class="import-block">
          <div class="import-title import-success">成功导入（{{ memberImportResult.importedNames.length }}）</div>
          <div class="import-names">{{ memberImportResult.importedNames.join('、') || '无' }}</div>
        </div>
        <div class="import-block">
          <div class="import-title import-warning">未导入（{{ memberImportResult.skippedNames.length }}）</div>
          <div class="import-names">{{ memberImportResult.skippedNames.join('、') || '无' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="memberImportResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 周记录编辑弹窗 -->
    <el-dialog v-model="recordEditVisible" title="编辑周记录" width="440px">
      <el-form label-width="90px">
        <el-form-item label="玩家名">
          <el-input :model-value="recordEditName" disabled />
        </el-form-item>
        <el-form-item label="忍战次数">
          <el-input-number v-model="recordForm.ninjaBattleCount" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总战力">
          <el-input-number v-model="recordForm.totalPower" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="战力增幅">
          <el-input-number v-model="recordForm.powerIncrease" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="铜币">
          <el-input-number v-model="recordForm.copperContribution" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="通灵兽">
          <el-input-number v-model="recordForm.beastSacrifice" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="叛忍">
          <el-input-number v-model="recordForm.renegadeCount" :min="0" :precision="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="车头">
          <el-switch v-model="recordForm.isRenegadeLeader" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRecord" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import dayjs from 'dayjs'
import {
  getOrgOrganizations,
  createOrgOrganization,
  updateOrgOrganization,
  deleteOrgOrganization,
  getOrgMembers,
  createOrgMember,
  updateOrgMember,
  updateOrgMemberStatus,
  getOrgPointsConfig,
  saveOrgPointsConfig,
  getOrgPackages,
  createOrgPackage,
  updateOrgPackage,
  deleteOrgPackage,
  getOrgWeeks,
  getOrgRecords,
  generateOrgRecords,
  calculateOrgRecords,
  importOrgExcel,
  importOrgMembers,
  setOrgRecordPackage,
  clearOrgRecordPackage,
  updateOrgRecord,
  deleteOrgWeek,
  getOrgApplications,
  approveOrgApplication,
  rejectOrgApplication,
} from '@/api/org'
import type {
  OrgImportResult,
  OrgMember,
  OrgMemberImportResult,
  OrgMemberRequest,
  OrgMembershipApplication,
  OrgOrganization,
  OrgOrganizationRequest,
  OrgPointsConfig,
  OrgRewardPackage,
  OrgRewardPackageRequest,
  OrgWeekRecord,
  OrgWeekRecordUpdateRequest,
} from '@/types/org'

const activeTab = ref('members')

// ==================== 组织 ====================
const organizations = ref<OrgOrganization[]>([])
const selectedOrgId = ref<number | null>(null)
const orgDialogVisible = ref(false)
const orgManageVisible = ref(false)
const editingOrgId = ref<number | null>(null)
const savingOrg = ref(false)
const orgForm = reactive<OrgOrganizationRequest>({ name: '' })

const loadOrganizations = async () => {
  const res = await getOrgOrganizations()
  organizations.value = res.data
  if (!selectedOrgId.value && organizations.value.length > 0) {
    selectedOrgId.value = organizations.value[0].id
  }
}
const openOrgDialog = (row?: OrgOrganization) => {
  editingOrgId.value = row?.id ?? null
  orgForm.name = row?.name ?? ''
  orgDialogVisible.value = true
}
const saveOrg = async () => {
  if (!orgForm.name.trim()) {
    ElMessage.warning('请输入组织名称')
    return
  }
  savingOrg.value = true
  try {
    if (editingOrgId.value) {
      await updateOrgOrganization(editingOrgId.value, orgForm)
    } else {
      await createOrgOrganization(orgForm)
    }
    ElMessage.success('保存成功')
    orgDialogVisible.value = false
    await loadOrganizations()
  } finally {
    savingOrg.value = false
  }
}
const deleteOrg = async (row: OrgOrganization) => {
  await ElMessageBox.confirm(`确定删除组织「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteOrgOrganization(row.id)
    ElMessage.success('删除成功')
    if (selectedOrgId.value === row.id) selectedOrgId.value = null
    await loadOrganizations()
  } catch (e) {
    // 后端会提示「仍有成员无法删除」
  }
}

// ==================== 成员 ====================
const members = ref<OrgMember[]>([])
const memberKeyword = ref('')
const filteredMembers = computed(() => {
  const kw = memberKeyword.value.trim().toLowerCase()
  if (!kw) return members.value
  return members.value.filter((m) => m.name.toLowerCase().includes(kw))
})
const memberDialogVisible = ref(false)
const editingMemberId = ref<number | null>(null)
const savingMember = ref(false)
const memberForm = reactive<OrgMemberRequest>({ organizationId: 0, name: '', position: '' })

const loadMembers = async () => {
  const res = await getOrgMembers()
  members.value = res.data
}
const openMemberDialog = (row?: OrgMember) => {
  editingMemberId.value = row?.id ?? null
  memberForm.organizationId = row?.organizationId ?? (selectedOrgId.value ?? 0)
  memberForm.name = row?.name ?? ''
  memberForm.position = row?.position ?? ''
  memberDialogVisible.value = true
}
const saveMember = async () => {
  if (!memberForm.organizationId) {
    ElMessage.warning('请选择所属组织')
    return
  }
  if (!memberForm.name.trim()) {
    ElMessage.warning('请输入玩家名')
    return
  }
  savingMember.value = true
  try {
    if (editingMemberId.value) {
      await updateOrgMember(editingMemberId.value, memberForm)
    } else {
      await createOrgMember(memberForm)
    }
    ElMessage.success('保存成功')
    memberDialogVisible.value = false
    await loadMembers()
  } finally {
    savingMember.value = false
  }
}
const toggleStatus = async (row: OrgMember) => {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '恢复加入' : '离开组织'
  await ElMessageBox.confirm(`确定${action}「${row.name}」？`, '提示', { type: 'warning' })
  await updateOrgMemberStatus(row.id, target)
  ElMessage.success('操作成功')
  await loadMembers()
}

// ==================== 成员导入 ====================
const memberImportOrgId = ref<number | null>(null)
const memberImportFile = ref<File | null>(null)
const importingMembers = ref(false)
const memberImportResultVisible = ref(false)
const memberImportResult = reactive<OrgMemberImportResult>({ importedNames: [], skippedNames: [] })

const onMemberFileChange = (file: UploadFile) => {
  memberImportFile.value = file.raw ?? null
}
const doMemberImport = async () => {
  if (!memberImportOrgId.value) return
  if (!memberImportFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importingMembers.value = true
  try {
    const res = await importOrgMembers(memberImportFile.value, memberImportOrgId.value)
    memberImportResult.importedNames = res.data.importedNames ?? []
    memberImportResult.skippedNames = res.data.skippedNames ?? []
    memberImportResultVisible.value = true
    memberImportFile.value = null
    await loadMembers()
  } finally {
    importingMembers.value = false
  }
}

// ==================== 积分换算比 ====================
const savingConfig = ref(false)
const config = reactive<OrgPointsConfig>({
  ninjaBattlePoints: 20,
  ninjaBattleEnabled: 1,
  totalPowerPoints: 0.00005,
  totalPowerEnabled: 1,
  powerIncreasePoints: 0,
  powerIncreaseEnabled: 1,
  copperPoints: 0.02,
  copperEnabled: 1,
  beastPoints: 0.01,
  beastEnabled: 1,
  renegadePoints: 3,
  renegadeEnabled: 1,
  renegadeLeaderBonus: 50,
  renegadeLeaderEnabled: 1,
  noPackageAdjustment: 0,
  ninjaBattleVisible: 1,
  totalPowerVisible: 1,
  powerIncreaseVisible: 1,
  copperVisible: 1,
  beastVisible: 1,
  renegadeVisible: 1,
  renegadeLeaderVisible: 1,
})
interface ConfigRow {
  label: string
  pointsKey:
    | 'ninjaBattlePoints'
    | 'totalPowerPoints'
    | 'powerIncreasePoints'
    | 'copperPoints'
    | 'beastPoints'
    | 'renegadePoints'
    | 'renegadeLeaderBonus'
  enabledKey:
    | 'ninjaBattleEnabled'
    | 'totalPowerEnabled'
    | 'powerIncreaseEnabled'
    | 'copperEnabled'
    | 'beastEnabled'
    | 'renegadeEnabled'
    | 'renegadeLeaderEnabled'
  visibleKey:
    | 'ninjaBattleVisible'
    | 'totalPowerVisible'
    | 'powerIncreaseVisible'
    | 'copperVisible'
    | 'beastVisible'
    | 'renegadeVisible'
    | 'renegadeLeaderVisible'
}
const configRows: ConfigRow[] = [
  { label: '忍战次数（1次）', pointsKey: 'ninjaBattlePoints', enabledKey: 'ninjaBattleEnabled', visibleKey: 'ninjaBattleVisible' },
  { label: '总战力（1战力）', pointsKey: 'totalPowerPoints', enabledKey: 'totalPowerEnabled', visibleKey: 'totalPowerVisible' },
  { label: '战力增幅（1战力）', pointsKey: 'powerIncreasePoints', enabledKey: 'powerIncreaseEnabled', visibleKey: 'powerIncreaseVisible' },
  { label: '铜币贡献（1）', pointsKey: 'copperPoints', enabledKey: 'copperEnabled', visibleKey: 'copperVisible' },
  { label: '通灵兽献祭（1）', pointsKey: 'beastPoints', enabledKey: 'beastEnabled', visibleKey: 'beastVisible' },
  { label: '叛忍次数（1次）', pointsKey: 'renegadePoints', enabledKey: 'renegadeEnabled', visibleKey: 'renegadeVisible' },
  { label: '叛忍车头「是」', pointsKey: 'renegadeLeaderBonus', enabledKey: 'renegadeLeaderEnabled', visibleKey: 'renegadeLeaderVisible' },
]

const loadConfig = async () => {
  if (!selectedOrgId.value) return
  const res = await getOrgPointsConfig(selectedOrgId.value)
  Object.assign(config, res.data)
}
const saveConfig = async () => {
  if (!selectedOrgId.value) return
  savingConfig.value = true
  try {
    await saveOrgPointsConfig(selectedOrgId.value, { ...config })
    ElMessage.success('保存成功')
  } finally {
    savingConfig.value = false
  }
}

// ==================== 奖励礼包 ====================
const packages = ref<OrgRewardPackage[]>([])
const packageDialogVisible = ref(false)
const editingPackageId = ref<number | null>(null)
const savingPackage = ref(false)
const packageForm = reactive<OrgRewardPackageRequest>({ name: '', deductionRatio: 0 })

const loadPackages = async () => {
  if (!selectedOrgId.value) {
    packages.value = []
    return
  }
  const res = await getOrgPackages(selectedOrgId.value)
  packages.value = res.data
}
const openPackageDialog = (row?: OrgRewardPackage) => {
  editingPackageId.value = row?.id ?? null
  packageForm.name = row?.name ?? ''
  packageForm.deductionRatio = row ? Math.round(row.deductionRatio * 100) : 0
  packageDialogVisible.value = true
}
const savePackage = async () => {
  if (!selectedOrgId.value) return
  if (!packageForm.name.trim()) {
    ElMessage.warning('请输入礼包名称')
    return
  }
  savingPackage.value = true
  try {
    const payload: OrgRewardPackageRequest = {
      name: packageForm.name,
      deductionRatio: packageForm.deductionRatio / 100,
    }
    if (editingPackageId.value) {
      await updateOrgPackage(editingPackageId.value, payload)
    } else {
      await createOrgPackage(selectedOrgId.value, payload)
    }
    ElMessage.success('保存成功')
    packageDialogVisible.value = false
    await loadPackages()
  } finally {
    savingPackage.value = false
  }
}
const deletePackage = async (row: OrgRewardPackage) => {
  await ElMessageBox.confirm(`确定删除礼包「${row.name}」？`, '提示', { type: 'warning' })
  await deleteOrgPackage(row.id)
  ElMessage.success('删除成功')
  await loadPackages()
}

// ==================== 周记录 ====================
const weeks = ref<string[]>([])
const selectedWeek = ref('')
const records = ref<OrgWeekRecord[]>([])
const recordKeyword = ref('')
const filteredRecords = computed(() => {
  const kw = recordKeyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter((r) => r.memberName.toLowerCase().includes(kw))
})
const uploadFile = ref<File | null>(null)
const importing = ref(false)
const generateDate = ref('')
const importResultVisible = ref(false)
const importResult = reactive<OrgImportResult>({ importedNames: [], unmatchedNames: [], emptyNames: [] })

const loadWeeks = async () => {
  if (!selectedOrgId.value) {
    weeks.value = []
    selectedWeek.value = ''
    records.value = []
    return
  }
  const res = await getOrgWeeks(selectedOrgId.value)
  weeks.value = res.data
  if (!selectedWeek.value && weeks.value.length > 0) {
    selectedWeek.value = weeks.value[0]
  }
}
const loadRecords = async () => {
  if (!selectedOrgId.value || !selectedWeek.value) {
    records.value = []
    return
  }
  const res = await getOrgRecords(selectedOrgId.value, selectedWeek.value)
  records.value = res.data
}
const computeSunday = (dateStr: string): string => {
  const d = dayjs(dateStr)
  const day = d.day() // 0=周日
  return d.add(day === 0 ? 0 : 7 - day, 'day').format('YYYY-MM-DD')
}

const generate = async () => {
  if (!selectedOrgId.value) return
  const target = generateDate.value ? computeSunday(generateDate.value) : undefined
  const label = target ? `${target} 当周` : '本周（本周周日）'
  await ElMessageBox.confirm(`将为该组织${label}生成记录`, '一键生成', { type: 'info' })
  const res = await generateOrgRecords(selectedOrgId.value, target)
  ElMessage.success(`已生成 ${res.data} 条记录`)
  await loadWeeks()
  await loadRecords()
}
const onFileChange = (file: UploadFile) => {
  uploadFile.value = file.raw ?? null
}
const doImport = async () => {
  if (!selectedOrgId.value) return
  if (!uploadFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  try {
    const res = await importOrgExcel(uploadFile.value, selectedOrgId.value, selectedWeek.value || undefined)
    importResult.importedNames = res.data.importedNames ?? []
    importResult.unmatchedNames = res.data.unmatchedNames ?? []
    importResult.emptyNames = res.data.emptyNames ?? []
    importResultVisible.value = true
    uploadFile.value = null
    await loadWeeks()
    await loadRecords()
  } finally {
    importing.value = false
  }
}
const calculate = async () => {
  if (!selectedOrgId.value || !selectedWeek.value) return
  const res = await calculateOrgRecords(selectedOrgId.value, selectedWeek.value)
  ElMessage.success(`已计算 ${res.data} 条记录`)
  await loadRecords()
}
const handleSetPackage = async (row: OrgWeekRecord, val: number | undefined) => {
  if (val) {
    await setOrgRecordPackage(row.id, val)
    ElMessage.success('已设置礼包')
  } else {
    await clearOrgRecordPackage(row.id)
    ElMessage.success('已清除礼包')
  }
  await loadRecords()
}
const deleteWeek = async () => {
  if (!selectedOrgId.value || !selectedWeek.value) return
  await ElMessageBox.confirm(`确定删除整周（${selectedWeek.value}）记录？`, '提示', { type: 'warning' })
  await deleteOrgWeek(selectedOrgId.value, selectedWeek.value)
  ElMessage.success('删除成功')
  selectedWeek.value = ''
  await loadWeeks()
  await loadRecords()
}

// ==================== 周记录手动编辑 ====================
const recordEditVisible = ref(false)
const recordEditId = ref<number | null>(null)
const recordEditName = ref('')
const savingRecord = ref(false)
const recordForm = reactive<OrgWeekRecordUpdateRequest>({
  ninjaBattleCount: undefined,
  totalPower: undefined,
  powerIncrease: undefined,
  copperContribution: undefined,
  beastSacrifice: undefined,
  renegadeCount: undefined,
  isRenegadeLeader: 0,
})

const openRecordEdit = (row: OrgWeekRecord) => {
  recordEditId.value = row.id
  recordEditName.value = row.memberName
  recordForm.ninjaBattleCount = row.ninjaBattleCount ?? undefined
  recordForm.totalPower = row.totalPower ?? undefined
  recordForm.powerIncrease = row.powerIncrease ?? undefined
  recordForm.copperContribution = row.copperContribution ?? undefined
  recordForm.beastSacrifice = row.beastSacrifice ?? undefined
  recordForm.renegadeCount = row.renegadeCount ?? undefined
  recordForm.isRenegadeLeader = row.isRenegadeLeader ?? 0
  recordEditVisible.value = true
}

const saveRecord = async () => {
  if (!recordEditId.value) return
  savingRecord.value = true
  try {
    await updateOrgRecord(recordEditId.value, { ...recordForm })
    ElMessage.success('保存成功')
    recordEditVisible.value = false
    await loadRecords()
  } finally {
    savingRecord.value = false
  }
}

const fmt = (v: number | null | undefined): string => {
  if (v === null || v === undefined) return '-'
  return Number(v).toFixed(5)
}

// ==================== 加入审批 ====================
const applications = ref<OrgMembershipApplication[]>([])
const loadingApplications = ref(false)

const fmtTime = (v: string | null | undefined): string => {
  if (!v) return '-'
  return dayjs(v).format('YYYY-MM-DD HH:mm')
}

const loadApplications = async () => {
  loadingApplications.value = true
  try {
    const res = await getOrgApplications()
    applications.value = res.data
  } finally {
    loadingApplications.value = false
  }
}

const approveApplication = async (row: OrgMembershipApplication) => {
  await approveOrgApplication(row.id)
  ElMessage.success('已通过')
  await loadApplications()
}

const rejectApplication = async (row: OrgMembershipApplication) => {
  await ElMessageBox.confirm(
    `确定拒绝「${row.username}」加入「${row.organizationName}」？`,
    '提示',
    { type: 'warning' }
  )
  await rejectOrgApplication(row.id)
  ElMessage.success('已拒绝')
  await loadApplications()
}

watch(activeTab, (val) => {
  if (val === 'applications') loadApplications()
})

watch(selectedOrgId, () => {
  selectedWeek.value = ''
  loadConfig()
  loadPackages()
  loadWeeks()
  loadRecords()
})

onMounted(async () => {
  await loadOrganizations()
  await loadMembers()
  await loadConfig()
  await loadPackages()
  await loadWeeks()
  await loadRecords()
  await loadApplications()
})
</script>

<style scoped lang="scss">
.admin-org {
  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 13px;
      color: var(--ev-text-secondary);
    }
  }

  .config-panel {
    max-width: 520px;

    .config-item {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;

      .config-label {
        width: 130px;
        font-size: 13px;
        color: var(--ev-text-secondary);
      }

      .config-eq {
        font-size: 13px;
        color: var(--ev-text-muted);
      }

      .points-input {
        width: 150px;
      }
    }
  }

  .import-report {
    .import-block {
      margin-bottom: 16px;

      .import-title {
        font-size: 13px;
        font-weight: 600;
        margin-bottom: 6px;
      }

      .import-success {
        color: var(--ev-success, #67c23a);
      }

      .import-danger {
        color: var(--ev-danger, #f56c6c);
      }

      .import-warning {
        color: var(--ev-warning, #e6a23c);
      }

      .import-names {
        font-size: 13px;
        color: var(--ev-text-secondary);
        word-break: break-all;
      }
    }
  }

  :deep(.el-table th .cell) {
    white-space: nowrap;
  }
}
</style>
