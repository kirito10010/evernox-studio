<template>
  <div v-if="canUse" class="super-member-org">
    <AdminOrg />
  </div>
  <div v-else class="super-member-intro">
    <div class="intro-card">
      <h2 class="intro-title">组织积分</h2>
      <p class="intro-sub">超级会员专属功能</p>
      <div class="intro-desc">
        <p>面向火影忍者OL 游戏组织的周积分统计与功勋礼包管理：</p>
        <ul>
          <li>创建组织、维护组织成员与职务</li>
          <li>配置积分换算比与奖励礼包</li>
          <li>Excel 批量导入成员与每周活动数据</li>
          <li>一键生成周记录、计算积分、发放礼包</li>
          <li>审批成员的加入申请</li>
        </ul>
      </div>
      <el-button type="primary" size="large" @click="onUpgrade">
        使用积分升级为超级会员
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AdminOrg from '@/views/admin/AdminOrg.vue'

const router = useRouter()
const userStore = useUserStore()
const canUse = computed(() => userStore.isAdmin || userStore.isSuperMember)

onMounted(() => {
  // 点开时重新拉取身份，保证已到期的超级会员立即失效
  userStore.fetchUserInfo()
})

const onUpgrade = () => {
  router.push('/profile')
}
</script>

<style scoped lang="scss">
.super-member-intro {
  display: flex;
  justify-content: center;
  padding: 48px 0;

  .intro-card {
    max-width: 560px;
    width: 100%;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid var(--ev-border-subtle);
    border-radius: 16px;
    padding: 32px;
    text-align: center;

    .intro-title {
      font-size: 22px;
      font-weight: 700;
      color: var(--ev-text-primary);
      margin: 0 0 4px;
    }

    .intro-sub {
      font-size: 13px;
      color: var(--ev-primary);
      margin: 0 0 20px;
    }

    .intro-desc {
      text-align: left;
      font-size: 13px;
      color: var(--ev-text-secondary);
      line-height: 1.9;

      ul {
        margin: 8px 0 0;
        padding-left: 20px;
      }
    }
  }
}
</style>
