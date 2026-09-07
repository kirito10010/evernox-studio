<template>
  <div class="sandbox-page">
    <div class="page-header">
      <div class="header-text">
        <h2>沙盘争霸</h2>
        <p v-if="mapData" class="round-info">
          第 {{ mapData.roundNo }} 轮 · {{ roundStatusText }}
          <span v-if="mapData.roundStatus === 1 && mapData.settleAt" class="settle-hint">
            （周日 18:00 结算）
          </span>
        </p>
      </div>
      <el-button @click="showHelp = true">游戏说明</el-button>
    </div>

    <div class="sandbox-body">
      <div class="map-panel">
        <ChinaMap
          v-if="mapData"
          :cities="mapData.cities"
          :my-user-id="myUserId"
          @select="onSelectCity"
        />
        <div v-else v-loading="loading" class="map-loading">加载地图中…</div>
      </div>

      <aside class="side-panel">
        <!-- 玩家状态 -->
        <div class="card">
          <div class="card-title">我的状态</div>
          <template v-if="mapData && mapData.joined">
            <div class="stat-row">
              <span class="stat">兵力 <b>{{ mapData.force }}</b></span>
              <span class="stat">体力 <b>{{ mapData.actionPoints }}</b></span>
            </div>
            <div class="stat-row">
              <span class="stat">占城 <b>{{ mapData.ownedCityCount }}</b></span>
              <span class="stat">整省 <b>{{ mapData.completedProvinces }}</b></span>
            </div>
            <div class="stat-row">
              <span class="stat">势力 <b>{{ formatScore(mapData.powerScore) }}</b></span>
            </div>
          </template>
          <p v-else class="join-hint">
            {{ mapData && mapData.roundStatus === 1 ? '点击地图选择一个城市作为底盘加入本轮' : '当前不可加入' }}
          </p>
        </div>

        <!-- 未加入：选底盘 -->
        <div v-if="mapData && !mapData.joined && selectedCity && mapData.roundStatus === 1" class="card">
          <div class="card-title">选择底盘</div>
          <p class="city-name">{{ selectedCity.name }}（{{ selectedCity.province }}）</p>
          <el-button type="primary" :loading="joining" @click="doJoin">以此为底盘加入</el-button>
        </div>

        <!-- 已加入：选中城市操作 -->
        <div v-if="mapData && mapData.joined && selectedCity" class="card">
          <div class="card-title">城市：{{ selectedCity.name }}</div>
          <div class="city-meta">
            <span>等级 {{ selectedCity.level }} · 驻防 {{ selectedCity.garrison }}</span>
            <span class="owner-tag">{{ ownerLabel(selectedCity) }}</span>
          </div>

          <template v-if="mapData.roundStatus === 1">
            <div v-if="selectedCity.ownerUserId == null" class="action-row">
              <el-button type="primary" size="small" @click="act('occupy')">占领（耗 {{ selectedCity.baseDefense }} 兵力）</el-button>
            </div>

            <div v-else-if="selectedCity.ownerUserId === myUserId" class="action-row">
              <div class="force-input">
                <span>兵力</span>
                <el-input-number v-model="actionForce" :min="1" :max="mapData.force" size="small" />
              </div>
              <el-button size="small" type="primary" @click="act('garrison')">驻防</el-button>
              <el-button size="small" type="warning" @click="act('develop')">建设（耗 {{ developCost }}）</el-button>
            </div>

            <div v-else class="action-row">
              <div class="force-input">
                <span>投入兵力</span>
                <el-input-number v-model="actionForce" :min="1" :max="mapData.force" size="small" />
              </div>
              <span class="defense-hint">对方防御 {{ selectedDefense }}，需投入 &gt; {{ selectedDefense }} 才能占领</span>
              <el-button size="small" type="danger" @click="act('attack')">进攻</el-button>
            </div>
          </template>
        </div>

        <!-- 排行榜 -->
        <div class="card rank-card">
          <div class="card-title">势力榜</div>
          <div v-if="rankList.length" class="rank-list">
            <div v-for="item in rankList" :key="item.userId" class="rank-item" :class="{ me: item.userId === myUserId }">
              <span class="rank-no">{{ item.rank }}</span>
              <span class="rank-name">{{ item.username }}</span>
              <span class="rank-score">{{ formatScore(item.powerScore) }}</span>
            </div>
          </div>
          <p v-else class="muted">暂无玩家</p>
        </div>
      </aside>
    </div>

    <el-dialog v-model="showHelp" title="沙盘争霸 · 游戏说明" width="600px" append-to-body>
      <div class="help-body">
        <div class="help-section">
          <h4>周期</h4>
          <p>每周一 10:00 开局，周日 18:00 结算发奖，周一 10:00 重置地图。中途随时可加入。</p>
        </div>
        <div class="help-section">
          <h4>选底盘</h4>
          <p>加入时从全国市级单位选 1 个作为本营（本营永不可被夺），再逐步向外扩张。晚加入按已过天数补发启动兵力（每天 +600）。</p>
        </div>
        <div class="help-section">
          <h4>城市档位</h4>
          <p>全国城市分两档，决定「值钱程度（权重）」与「攻克难度（守备）」：</p>
          <ul>
            <li>省会 / 直辖市城区 / 特别行政区：权重 3、基础守备 150</li>
            <li>其余城市：权重 1、基础守备 100</li>
          </ul>
          <p>权重只影响势力值；守备只影响占领/进攻要投入的兵力门槛。两者互不影响。</p>
        </div>
        <div class="help-section">
          <h4>资源（每小时结算，离线最多累积 6 小时）</h4>
          <ul>
            <li>体力：每小时 +1，上限 20，每次操作消耗 1。</li>
            <li>兵力：每小时 +40 + 已占城市产出（每级 +3/小时）。</li>
            <li>晚加入补偿：加入基础兵力 800，之后每过一天额外 +600。</li>
          </ul>
        </div>
        <div class="help-section">
          <h4>操作</h4>
          <ul>
            <li>占领：占领「相邻且无主」的城市，消耗 = 该城基础守备。</li>
            <li>进攻：攻打「相邻且有主」的城市，需投入 &gt; 对方防御（基础守备 + 驻防 + 等级×10）才能占领；失败只损失投入兵力，对方无损。</li>
            <li>驻防：把兵力压入自己的城市，提高防御、防被抢。</li>
            <li>建设：花 50×(等级+1) 兵力升 1 级；每级 +3/小时产出、+10 防御、势力值 ×(1+0.2)。</li>
          </ul>
        </div>
        <div class="help-section">
          <h4>势力值</h4>
          <p>势力值 = Σ(权重 × (1 + 等级×0.2)) + 完整占领的省数量×5。</p>
          <p>兵力不计入势力。地盘越多、等级越高、占的省会越多，势力越高。占满一个省的所有城市额外 +5。</p>
        </div>
        <div class="help-section">
          <h4>奖励（周日 18:00 结算）</h4>
          <p>按势力值排名发积分（第 1 名 600 = 1 个月超级会员）：</p>
          <ul>
            <li>总榜：第 1 名 600、第 2 名 400、第 3 名 300、第 4-5 名 200、第 6-10 名 120、第 11-20 名 60、第 21-50 名 30。</li>
            <li>新星榜（中途加入者）：第 1 名 200、第 2 名 120、第 3 名 80（与总榜取较高者）。</li>
            <li>参与奖：占地 ≥1 城额外 +10 分。</li>
          </ul>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ChinaMap from '@/components/game/ChinaMap.vue'
import { doGameAction, getGameMap, getGameRank, joinGame } from '@/api/game'
import type { GameActionType, GameMapChangeEvent, GameMapResponse, GameRankItem } from '@/types/game'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const myUserId = computed(() => userStore.userInfo?.id ?? null)

const mapData = ref<GameMapResponse | null>(null)
const rankList = ref<GameRankItem[]>([])
const selectedCityId = ref<number | null>(null)
const actionForce = ref(1)
const loading = ref(false)
const joining = ref(false)
const showHelp = ref(false)

let eventSource: EventSource | null = null

const roundStatusText = computed(() => {
  const s = mapData.value?.roundStatus
  if (s === 1) return '进行中'
  if (s === 2) return '已结算'
  return '未开始'
})

const selectedCity = computed(() => {
  if (!mapData.value || selectedCityId.value == null) return null
  return mapData.value.cities.find((c) => c.id === selectedCityId.value) ?? null
})

const developCost = computed(() => {
  if (!selectedCity.value) return 0
  return 50 * (selectedCity.value.level + 1)
})

const selectedDefense = computed(() => {
  if (!selectedCity.value) return 0
  return selectedCity.value.baseDefense + selectedCity.value.garrison + selectedCity.value.level * 10
})

const formatScore = (v: number): string => (Number(v) || 0).toFixed(1)

const ownerLabel = (c: { ownerUserId: number | null }): string => {
  if (c.ownerUserId == null) return '无主'
  if (c.ownerUserId === myUserId.value) return '我方'
  return '他人'
}

const loadMap = async () => {
  loading.value = true
  try {
    const res = await getGameMap()
    mapData.value = res.data ?? null
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

const loadRank = async () => {
  try {
    const res = await getGameRank()
    rankList.value = res.data ?? []
  } catch {
    rankList.value = []
  }
}

const onSelectCity = (cityId: number) => {
  selectedCityId.value = cityId
}

const doJoin = async () => {
  if (!selectedCity.value) return
  joining.value = true
  try {
    await joinGame(selectedCity.value.id)
    ElMessage.success('已加入本轮')
    await Promise.all([loadMap(), loadRank()])
  } catch {
    // 请求层已提示
  } finally {
    joining.value = false
  }
}

const act = async (type: GameActionType) => {
  if (!selectedCity.value) return
  const payload = { type, cityId: selectedCity.value.id, force: actionForce.value }
  try {
    await doGameAction(payload)
    await loadMap()
    await loadRank()
  } catch {
    // 请求层已提示
  }
}

const applyMapChange = (ev: GameMapChangeEvent) => {
  if (!mapData.value) return
  const city = mapData.value.cities.find((c) => c.id === ev.cityId)
  if (city) {
    city.ownerUserId = ev.ownerUserId
    city.level = ev.level
    city.garrison = ev.garrison
  }
}

const connectStream = () => {
  const token = localStorage.getItem('accessToken')
  if (!token) return
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  eventSource = new EventSource(`${base}/game/stream?token=${encodeURIComponent(token)}`)
  eventSource.addEventListener('map-change', (e) => {
    try {
      applyMapChange(JSON.parse((e as MessageEvent).data) as GameMapChangeEvent)
    } catch {
      // 忽略解析失败
    }
  })
}

onMounted(() => {
  loadMap()
  loadRank()
  connectStream()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped lang="scss">
.sandbox-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}

.page-header {
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: var(--ev-text-primary);
  }

  .round-info {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--ev-text-secondary);
  }

  .settle-hint {
    color: var(--ev-text-muted);
  }
}

.sandbox-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 14px;
}

.map-panel {
  position: relative;
  min-height: 0;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 16px;
  overflow: hidden;
  background: var(--ev-bg-glass);
}

.map-loading {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ev-text-muted);
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.card {
  padding: 14px;
  background: var(--ev-bg-glass);
  border: 1px solid var(--ev-border-subtle);
  border-radius: 14px;

  .card-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--ev-text-primary);
    margin-bottom: 10px;
  }
}

.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 4px;

  .stat {
    font-size: 13px;
    color: var(--ev-text-secondary);

    b {
      color: var(--ev-text-primary);
      font-variant-numeric: tabular-nums;
    }
  }
}

.join-hint {
  font-size: 13px;
  color: var(--ev-text-secondary);
  margin: 0;
}

.city-name {
  font-size: 14px;
  color: var(--ev-text-primary);
  margin: 0 0 10px;
}

.city-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--ev-text-secondary);
  margin-bottom: 10px;

  .owner-tag {
    color: var(--ev-primary);
  }
}

.action-row {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .force-input {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: var(--ev-text-secondary);
  }

  .defense-hint {
    font-size: 12px;
    color: var(--ev-text-muted);
  }
}

.rank-list {
  max-height: 260px;
  overflow-y: auto;

  .rank-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    font-size: 13px;
    color: var(--ev-text-secondary);

    &.me {
      color: var(--ev-primary);
      font-weight: 600;
    }

    .rank-no {
      width: 24px;
      text-align: center;
      flex-shrink: 0;
    }

    .rank-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .rank-score {
      font-variant-numeric: tabular-nums;
    }
  }
}

.muted {
  font-size: 13px;
  color: var(--ev-text-muted);
}

.help-body {
  max-height: 60vh;
  overflow-y: auto;

  .help-section {
    margin-bottom: 14px;

    h4 {
      margin: 0 0 6px;
      font-size: 14px;
      font-weight: 700;
      color: var(--ev-text-primary);
    }

    p,
    li {
      margin: 0;
      font-size: 13px;
      line-height: 1.7;
      color: var(--ev-text-secondary);
    }

    ul {
      margin: 0;
      padding-left: 18px;
    }
  }
}
</style>
