<template>
  <div class="admin-org">
    <el-tabs v-model="activeTab" class="org-tabs">
      <!-- ==================== 成员管理 ==================== -->
      <el-tab-pane label="成员管理" name="members" lazy>
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
          <el-button link type="primary" @click="importHelpVisible = true">导入说明</el-button>
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
      <el-tab-pane label="积分换算比" name="config" lazy>
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
      <el-tab-pane label="奖励礼包" name="packages" lazy>
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
      <el-tab-pane label="周记录" name="records" lazy>
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
          <el-button link type="primary" @click="importHelpVisible = true">导入说明</el-button>
        </div>

        <el-table :data="pagedRecords" border stripe :default-sort="{ prop: 'totalPoints', order: 'descending' }" @sort-change="handleSortChange">
          <el-table-column prop="memberName" label="玩家名" min-width="90" fixed="left" />
          <el-table-column prop="position" label="职务" min-width="90" />
          <el-table-column prop="ninjaBattleCount" v-if="config.ninjaBattleVisible === 1" label="忍战次数" min-width="90" sortable="custom" />
          <el-table-column prop="totalPower" v-if="config.totalPowerVisible === 1" label="总战力" min-width="100" sortable="custom" />
          <el-table-column prop="powerIncrease" v-if="config.powerIncreaseVisible === 1" label="战力增幅" min-width="90" sortable="custom" />
          <el-table-column prop="copperContribution" v-if="config.copperVisible === 1" label="铜币" min-width="80" sortable="custom" />
          <el-table-column prop="beastSacrifice" v-if="config.beastVisible === 1" label="通灵兽" min-width="80" sortable="custom" />
          <el-table-column prop="renegadeCount" v-if="config.renegadeVisible === 1" label="叛忍" min-width="70" sortable="custom" />
          <el-table-column v-if="config.renegadeLeaderVisible === 1" label="车头" min-width="70">
            <template #default="{ row }">{{ row.isRenegadeLeader === 1 ? '是' : '' }}</template>
          </el-table-column>
          <el-table-column label="上周剩余" min-width="100" sortable="custom" prop="lastWeekPoints">
            <template #default="{ row }">{{ fmt(row.lastWeekPoints) }}</template>
          </el-table-column>
          <el-table-column label="本周积分" min-width="100" sortable="custom" prop="thisWeekPoints">
            <template #default="{ row }">{{ fmt(row.thisWeekPoints) }}</template>
          </el-table-column>
          <el-table-column label="总积分" min-width="100" sortable="custom" prop="totalPoints">
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
          <el-table-column label="扣除后积分" width="110" fixed="right" sortable="custom" prop="pointsAfterDeduction">
            <template #default="{ row }">{{ fmt(row.pointsAfterDeduction) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRecordEdit(row as OrgWeekRecord)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="records-pagination">
          <el-pagination
            v-model:current-page="recordPage"
            v-model:page-size="recordPageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredRecords.length"
            layout="total, sizes, prev, pager, next, jumper"
            small
          />
        </div>
      </el-tab-pane>

      <!-- ==================== 加入审批 ==================== -->
      <el-tab-pane label="加入审批" name="applications" lazy>
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

    <!-- 成员导入差异预览弹窗 -->
    <el-dialog v-model="memberPreviewVisible" title="成员导入预览" width="640px">
      <div class="member-preview">
        <div class="preview-block">
          <div class="preview-title">
            新增成员（勾选加入，{{ selectedAdd.length }}/{{ memberPreview.toAdd.length }}）
          </div>
          <el-table
            :data="memberPreview.toAdd"
            size="small"
            max-height="200"
            border
            @selection-change="onAddSelection"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="玩家名" min-width="140" />
            <el-table-column prop="position" label="职务" min-width="120" />
          </el-table>
          <el-empty v-if="memberPreview.toAdd.length === 0" description="无新增成员" :image-size="60" />
        </div>

        <div class="preview-block">
          <div class="preview-title">无变动成员（{{ memberPreview.unchangedNames.length }}）</div>
          <div class="preview-names">{{ memberPreview.unchangedNames.join('、') || '无' }}</div>
        </div>

        <div class="preview-block">
          <div class="preview-title">
            恢复候选（勾选恢复为在组织，{{ selectedRestore.length }}/{{ memberPreview.toRestore.length }}）
          </div>
          <el-table
            :data="memberPreview.toRestore"
            size="small"
            max-height="200"
            border
            @selection-change="onRestoreSelection"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="玩家名" min-width="140" />
            <el-table-column prop="position" label="职务" min-width="120" />
          </el-table>
          <el-empty v-if="memberPreview.toRestore.length === 0" description="无恢复候选" :image-size="60" />
        </div>

        <div class="preview-block">
          <div class="preview-title">
            职务替换（勾选替换，{{ selectedUpdate.length }}/{{ memberPreview.toUpdate.length }}）

          </div>
          <el-table
            :data="memberPreview.toUpdate"
            size="small"
            max-height="200"
            border
            @selection-change="onUpdateSelection"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="玩家名" min-width="120" />
            <el-table-column label="原职务" min-width="110">
              <template #default="{ row }">{{ row.oldPosition || '-' }}</template>
            </el-table-column>
            <el-table-column label="新职务" min-width="110">
              <template #default="{ row }">{{ row.newPosition || '-' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="memberPreview.toUpdate.length === 0" description="无职务替换" :image-size="60" />
        </div>

        <div class="preview-block">
          <div class="preview-title">
            离开候选（勾选设为离开组织，{{ selectedLeave.length }}/{{ memberPreview.toLeave.length }}）
          </div>
          <el-table
            :data="memberPreview.toLeave"
            size="small"
            max-height="200"
            border
            @selection-change="onLeaveSelection"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="玩家名" min-width="140" />
            <el-table-column prop="position" label="职务" min-width="120" />
          </el-table>
          <el-empty v-if="memberPreview.toLeave.length === 0" description="无离开候选" :image-size="60" />
        </div>
      </div>
      <template #footer>
        <el-button @click="memberPreviewVisible = false">取消导入</el-button>
        <el-button
          type="primary"
          :loading="applyingMembers"
          :disabled="selectedAdd.length === 0 && selectedUpdate.length === 0 && selectedRestore.length === 0 && selectedLeave.length === 0"
          @click="confirmMemberImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入说明弹窗 -->
    <el-dialog v-model="importHelpVisible" title="Excel 导入说明" width="620px" append-to-body>
      <div class="import-help">
        <div class="help-section">
          <h4>成员管理 · 导入成员</h4>
          <p>第一行必须是表头，列名如下：</p>
          <ul>
            <li><b>成员</b>：玩家名（必填，也接受「角色名字 / 名称 / 玩家名」）</li>
            <li><b>职务</b>：职务（可选，也接受「职位」）</li>
          </ul>
          <p>导入时会先预览差异，再勾选新增 / 恢复 / 替换职务 / 离开，最后确认，可随时取消。</p>
        </div>
        <div class="help-section">
          <h4>周记录 · 导入数据</h4>
          <p>第一行必须是表头，列名如下：</p>
          <ul>
            <li><b>成员</b>：玩家名（必填，也接受「角色名字 / 名称 / 玩家名」）</li>
            <li><b>参战次数</b> → 忍战次数</li>
            <li><b>战斗力</b> → 总战力</li>
            <li><b>捐献贡献</b> → 铜币</li>
            <li><b>献祭通灵查克拉</b> → 通灵兽</li>
            <li><b>缉拿叛忍数</b> → 叛忍</li>
            <li><b>车头</b> → 叛忍车头</li>
          </ul>
          <p>「战力增幅」不需要在 Excel 里填：点「计算积分」时自动按「本周战斗力 − 上周战斗力」计算。</p>
          <p>使用前提：先在该周「一键生成」记录，再「导入数据」。</p>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="importHelpVisible = false">知道了</el-button>
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
  previewOrgMembers,
  applyOrgMembers,
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
  OrgMemberImportApplyRequest,
  OrgMemberImportApplyResult,
  OrgMemberImportCandidate,
  OrgMemberImportPreviewResult,
  OrgMemberImportUpdateCandidate,
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
const applyingMembers = ref(false)
const importHelpVisible = ref(false)
const memberPreviewVisible = ref(false)
const memberPreview = reactive<OrgMemberImportPreviewResult>({
  toAdd: [],
  unchangedNames: [],
  toRestore: [],
  toUpdate: [],
  toLeave: [],
})
const selectedAdd = ref<OrgMemberImportCandidate[]>([])
const selectedUpdate = ref<OrgMemberImportUpdateCandidate[]>([])
const selectedRestore = ref<OrgMember[]>([])
const selectedLeave = ref<OrgMember[]>([])

const onMemberFileChange = (file: UploadFile) => {
  memberImportFile.value = file.raw ?? null
}
const onAddSelection = (rows: OrgMemberImportCandidate[]) => {
  selectedAdd.value = rows
}
const onUpdateSelection = (rows: OrgMemberImportUpdateCandidate[]) => {
  selectedUpdate.value = rows
}
const onRestoreSelection = (rows: OrgMember[]) => {
  selectedRestore.value = rows
}
const onLeaveSelection = (rows: OrgMember[]) => {
  selectedLeave.value = rows
}
const doMemberImport = async () => {
  if (!memberImportOrgId.value) return
  if (!memberImportFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importingMembers.value = true
  try {
    const res = await previewOrgMembers(memberImportFile.value, memberImportOrgId.value)
    memberPreview.toAdd = res.data.toAdd ?? []
    memberPreview.unchangedNames = res.data.unchangedNames ?? []
    memberPreview.toRestore = res.data.toRestore ?? []
    memberPreview.toUpdate = res.data.toUpdate ?? []
    memberPreview.toLeave = res.data.toLeave ?? []
    selectedAdd.value = []
    selectedUpdate.value = []
    selectedRestore.value = []
    selectedLeave.value = []
    memberPreviewVisible.value = true
  } finally {
    importingMembers.value = false
  }
}
const confirmMemberImport = async () => {
  if (!memberImportOrgId.value) return
  applyingMembers.value = true
  try {
    const payload: OrgMemberImportApplyRequest = {
      organizationId: memberImportOrgId.value,
      add: selectedAdd.value.map((c) => ({ name: c.name, position: c.position })),
      updates: selectedUpdate.value,
      restoreIds: selectedRestore.value.map((m) => m.id),
      leaveIds: selectedLeave.value.map((m) => m.id),
    }
    const res = await applyOrgMembers(payload)
    const r: OrgMemberImportApplyResult = res.data
    ElMessage.success(
      `导入完成：新增 ${r.addedNames?.length ?? 0} 人、恢复 ${r.restoredNames?.length ?? 0} 人、替换职务 ${r.updatedNames?.length ?? 0} 人、离开 ${r.leftNames?.length ?? 0} 人、跳过 ${r.skippedNames?.length ?? 0} 人`
    )
    memberPreviewVisible.value = false
    memberImportFile.value = null
    await loadMembers()
  } finally {
    applyingMembers.value = false
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
const recordPage = ref(1)
const recordPageSize = ref(20)
const filteredRecords = computed(() => {
  const kw = recordKeyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter((r) => r.memberName.toLowerCase().includes(kw))
})
const sortState = reactive<{ prop: string; order: 'ascending' | 'descending' }>({
  prop: 'totalPoints',
  order: 'descending',
})
const sortedRecords = computed(() => {
  const { prop, order } = sortState
  const arr = [...filteredRecords.value]
  if (!prop) return arr
  const dir = order === 'ascending' ? 1 : -1
  arr.sort((a, b) => {
    const av = (a as unknown as Record<string, unknown>)[prop]
    const bv = (b as unknown as Record<string, unknown>)[prop]
    const an = av == null ? null : Number(av)
    const bn = bv == null ? null : Number(bv)
    if (an == null && bn == null) return 0
    if (an == null) return 1
    if (bn == null) return -1
    return (an - bn) * dir
  })
  return arr
})
const pagedRecords = computed(() => {
  const start = (recordPage.value - 1) * recordPageSize.value
  return sortedRecords.value.slice(start, start + recordPageSize.value)
})
const handleSortChange = ({ prop, order }: { prop: string | null; order: 'ascending' | 'descending' | null }) => {
  sortState.prop = prop || 'totalPoints'
  sortState.order = order || 'descending'
}
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
  recordPage.value = 1
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

watch(recordKeyword, () => {
  recordPage.value = 1
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

  .records-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
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

  .member-preview {
    .preview-block {
      margin-bottom: 16px;

      .preview-title {
        font-size: 13px;
        font-weight: 600;
        margin-bottom: 6px;
        color: var(--ev-text-primary);
      }

      .preview-names {
        font-size: 13px;
        color: var(--ev-text-secondary);
        word-break: break-all;
        max-height: 80px;
        overflow-y: auto;
      }
    }
  }

  :deep(.el-table th .cell) {
    white-space: nowrap;
  }
}

.import-help {
  max-height: 60vh;
  overflow-y: auto;

  .help-section {
    margin-bottom: 16px;

    h4 {
      margin: 0 0 6px;
      font-size: 14px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p {
      margin: 0 0 4px;
      font-size: 13px;
      line-height: 1.7;
      color: var(--ev-text-secondary);
    }

    ul {
      margin: 4px 0 0;
      padding-left: 20px;
      list-style: disc;

      li {
        font-size: 13px;
        line-height: 1.7;
        color: var(--ev-text-secondary);
      }
    }
  }
}
</style>
