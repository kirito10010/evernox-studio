<template>
  <div class="activity-detail">
    <div class="page-header">
      <h2>{{ activeName }}</h2>
      <el-select v-model="activeActivity" class="activity-select" placeholder="选择活动">
        <el-option
          v-for="activity in activities"
          :key="activity.key"
          :label="activity.name"
          :value="activity.key"
          :disabled="!activity.ready"
        >
          <span class="option-name">{{ activity.name }}</span>
          <span v-if="!activity.ready" class="option-hint">敬请期待</span>
        </el-option>
      </el-select>
      <span class="page-hint">火影忍者OL 活动资料库</span>
    </div>

    <MallDiscount v-if="activeActivity === 'mall'" />
    <FlagOffering v-else-if="activeActivity === 'flag'" />
    <BattleSummon v-else-if="activeActivity === 'battle-summon'" />
    <el-empty v-else :description="`「${activeName}」资料整理中`" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import MallDiscount from './activity/MallDiscount.vue'
import FlagOffering from './activity/FlagOffering.vue'
import BattleSummon from './activity/BattleSummon.vue'

interface ActivityOption {
  key: string
  name: string
  /** 只有做完的活动可选，其余在下拉里显示「敬请期待」 */
  ready: boolean
}

const activities: ActivityOption[] = [
  { key: 'mall', name: '商城特惠', ready: true },
  { key: 'flag', name: '彩旗献礼', ready: true },
  { key: 'battle-summon', name: '战场的召唤', ready: true },
  { key: 'carnival', name: '超级嘉年华', ready: false },
  { key: 'shopping-festival', name: '木叶购物节', ready: false },
  { key: 'auction', name: '拍卖会', ready: false },
]

const activeActivity = ref('mall')

const activeName = computed(
  () => activities.find((activity) => activity.key === activeActivity.value)?.name || ''
)
</script>

<style scoped>
.activity-detail {
  padding: 18px 22px 36px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.page-header h2 {
  margin: 0;
  font-size: 19px;
  color: var(--ev-text-primary);
  white-space: nowrap;
}

.activity-select {
  width: 150px;
}

.option-name {
  float: left;
}

.option-hint {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: #8aa3bb;
}

.page-hint {
  font-size: 12px;
  color: #8aa3bb;
}

@media (max-width: 900px) {
  .activity-detail {
    padding: 14px;
  }

  .page-hint {
    display: none;
  }
}
</style>
