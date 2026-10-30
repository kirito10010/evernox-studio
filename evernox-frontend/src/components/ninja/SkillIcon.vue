<template>
  <el-popover
    v-if="skill"
    placement="top"
    :width="344"
    trigger="hover"
    :show-after="120"
    :hide-after="60"
    popper-class="skill-popover"
  >
    <template #reference>
      <button
        type="button"
        class="skill-icon"
        :class="[`size-${size}`, typeClass, { 'is-empty': !iconUrl || failed }]"
        :aria-label="`${skill.type} ${skill.name || ''}`"
      >
        <img
          v-if="iconUrl && !failed"
          :src="iconUrl"
          :alt="skill.name || skill.type"
          loading="lazy"
          @error="failed = true"
        />
        <span v-else class="fallback">{{ typeShort }}</span>
        <span v-if="badge" class="badge" :class="badgeClass">{{ badge }}</span>
      </button>
    </template>

    <div class="skill-pop">
      <div class="pop-head">
        <span class="pop-type" :class="typeClass">{{ skill.type }}</span>
        <span class="pop-name">{{ skill.name || skill.type }}</span>
        <span v-if="badge" class="pop-badge" :class="badgeClass">{{ badge }}</span>
        <span v-if="skill.immediately" class="pop-fast">瞬发</span>
      </div>

      <div class="pop-stats">
        <span>冷却回合 <b>{{ skill.cd }}</b></span>
        <span>进场冷却 <b>{{ skill.enterCd }}</b></span>
        <span>消耗查克拉 <b>{{ skill.mp }}</b></span>
      </div>

      <div v-if="skill.desc" class="pop-desc" v-html="descHtml"></div>

      <div v-if="skill.linkName" class="pop-link">
        <span class="pop-label">链接目标</span>
        <span class="pop-link-name">{{ skill.linkName }}</span>
        <div v-if="skill.linkDesc" class="pop-desc" v-html="renderDesc(skill.linkDesc)"></div>
      </div>

      <div v-if="skill.states?.length" class="pop-states">
        <span class="pop-label">状态</span>
        <span
          v-for="s in skill.states"
          :key="s.name"
          class="state-chip"
          :title="s.desc"
        >{{ s.name }}</span>
      </div>

      <details v-if="showBase" class="pop-diff">
        <summary>基础效果</summary>
        <div class="pop-desc" v-html="renderDesc(baseSkill?.desc || '')"></div>
      </details>
    </div>
  </el-popover>
  <span v-else class="skill-empty">—</span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface SkillState {
  name: string
  desc: string
}

interface NinjaSkill {
  id: string
  name: string
  desc: string
  type: string
  icon: string
  cd: number
  enterCd: number
  mp: number
  immediately: boolean
  states: SkillState[]
  linkName?: string
  linkDesc?: string
}

const props = withDefaults(defineProps<{
  skill: NinjaSkill | null
  iconUrl?: string
  badge?: string
  size?: 'sm' | 'md'
  baseSkill?: NinjaSkill | null
}>(), {
  iconUrl: '',
  badge: '',
  size: 'md',
  baseSkill: null,
})

const failed = ref(false)
watch(() => props.iconUrl, () => { failed.value = false })

const TYPE_CLASS: Record<string, string> = {
  奥义: 'ultimate',
  普攻: 'normal',
  追打: 'chase',
  触发: 'trigger',
  被动: 'passive',
  其他: 'other',
}
const TYPE_SHORT: Record<string, string> = {
  奥义: '奥',
  普攻: '普',
  追打: '追',
  触发: '触',
  被动: '被',
  其他: '技',
}

const typeClass = computed(() => TYPE_CLASS[props.skill?.type || ''] || 'other')
const typeShort = computed(() => TYPE_SHORT[props.skill?.type || ''] || '技')
const badgeClass = computed(() => {
  switch (props.badge) {
    case '+1': return 'b1'
    case '+2': return 'b2'
    case '+3': return 'b3'
    case '+4': return 'b4'
    case 'Y': return 'by'
    case 'L': return 'bl'
    default: return ''
  }
})

const showBase = computed(() =>
  !!props.baseSkill
  && props.baseSkill.id !== props.skill?.id
  && !!props.baseSkill.desc
  && props.baseSkill.desc !== props.skill?.desc
)

const descHtml = computed(() => renderDesc(props.skill?.desc || ''))

/** 只保留 <font color>，其余标签全部剔除 */
function renderDesc(desc: string): string {
  let s = String(desc || '')
    .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>').replace(/&quot;/gi, '"').replace(/&#39;/g, "'")
    .replace(/&amp;/gi, '&')
    .replace(/<br\s*\/?>/gi, '\n')
  const tokens: string[] = []
  s = s.replace(/<\s*\/?\s*font[^>]*>/gi, m => {
    let out = ''
    if (/^<\s*\//.test(m)) {
      out = '</font>'
    } else {
      const c = /color\s*=\s*["']?(#[0-9a-fA-F]{3,8})/i.exec(m)
      if (c) out = `<font color="${c[1]}">`
    }
    tokens.push(out)
    return `\u0000${tokens.length - 1}\u0000`
  })
  s = s.replace(/<[^>]*>/g, '')
  s = s.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  s = s.replace(/&(?!(amp|lt|gt|quot|#\d+);)/g, '&amp;')
  s = s.replace(/\u0000(\d+)\u0000/g, (_, i) => tokens[Number(i)])
  return s.replace(/\n/g, '<br>')
}
</script>

<style scoped lang="scss">
.skill-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 9px;
  background: var(--ev-bg-tint);
  cursor: pointer;
  overflow: visible;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;

  &:hover {
    border-color: rgba(var(--ev-primary-rgb), 0.55);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.16);
    transform: translateY(-1px);
  }

  &.size-md {
    width: 48px;
    height: 48px;
    border-radius: 10px;
  }

  &.size-sm {
    width: 34px;
    height: 34px;
    border-radius: 7px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    border-radius: inherit;
    display: block;
  }

  .fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: inherit;
    color: #fff;
    font-weight: 700;
    font-size: 14px;
  }

  &.size-sm .fallback { font-size: 12px; }

  &.ultimate .fallback { background: var(--ev-primary); }
  &.normal .fallback { background: #4aa8a0; }
  &.chase .fallback { background: #e6a23c; }
  &.trigger .fallback { background: #0ea5a4; }
  &.passive .fallback { background: #8a94a6; }
  &.other .fallback { background: var(--ev-text-muted); }

  .badge {
    position: absolute;
    top: -5px;
    right: -6px;
    min-width: 16px;
    height: 16px;
    padding: 0 3px;
    border-radius: 5px;
    font-size: 9px;
    line-height: 16px;
    font-weight: 800;
    color: #fff;
    text-align: center;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
    pointer-events: none;

    &.b1 { background: #3b82f6; }
    &.b2 { background: #8b5cf6; }
    &.b3 { background: #0ea5a4; }
    &.b4 { background: #6366f1; }
    &.by { background: #f59e0b; }
    &.bl { background: #10b981; }
  }
}

.skill-empty {
  font-size: 13px;
  color: var(--ev-text-muted);
}
</style>

<style lang="scss">
.skill-popover.el-popover {
  padding: 0;
  border-radius: 12px;
  overflow: hidden;
}

.skill-popover {
  .skill-pop {
    padding: 12px 14px 13px;
    background: var(--ev-bg-surface);
    color: var(--ev-text-regular);
  }

  .pop-head {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
  }

  .pop-type {
    font-size: 10px;
    line-height: 18px;
    padding: 0 6px;
    border-radius: 5px;
    color: #fff;

    &.ultimate { background: var(--ev-primary); }
    &.normal { background: #4aa8a0; }
    &.chase { background: #e6a23c; }
    &.trigger { background: #0ea5a4; }
    &.passive { background: #8a94a6; }
    &.other { background: var(--ev-text-muted); }
  }

  .pop-name {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 700;
    color: var(--ev-text-primary);
    word-break: break-word;
  }

  .pop-badge,
  .pop-fast {
    font-size: 10px;
    line-height: 17px;
    padding: 0 6px;
    border-radius: 5px;
    font-weight: 700;
    color: #fff;

    &.b1 { background: #3b82f6; }
    &.b2 { background: #8b5cf6; }
    &.b3 { background: #0ea5a4; }
    &.b4 { background: #6366f1; }
    &.by { background: #f59e0b; }
    &.bl { background: #10b981; }
  }

  .pop-fast {
    background: #d98a1f;
  }

  .pop-stats {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    margin-top: 8px;
    padding: 6px 8px;
    border-radius: 7px;
    background: var(--ev-bg-tint);
    font-size: 11px;
    color: var(--ev-text-muted);

    b {
      color: var(--ev-text-primary);
      font-weight: 700;
    }
  }

  .pop-desc {
    margin-top: 8px;
    font-size: 12.5px;
    line-height: 1.7;
    color: var(--ev-text-regular);
    word-break: break-word;

    font { font-weight: 600; }
  }

  .pop-link {
    margin-top: 9px;
    padding-top: 8px;
    border-top: 1px dashed var(--ev-border-subtle);
  }

  .pop-link-name {
    margin-left: 6px;
    font-size: 12px;
    font-weight: 600;
    color: var(--ev-primary);
  }

  .pop-label {
    display: inline-block;
    margin-top: 9px;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: var(--ev-text-muted);
  }

  .pop-states {
    margin-top: 2px;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 5px;
  }

  .state-chip {
    font-size: 10px;
    line-height: 18px;
    padding: 0 7px;
    border-radius: 999px;
    background: rgba(var(--ev-primary-rgb), 0.1);
    color: var(--ev-primary);
    cursor: help;
  }

  .pop-diff {
    margin-top: 9px;
    padding-top: 8px;
    border-top: 1px dashed var(--ev-border-subtle);
    font-size: 12px;

    summary {
      cursor: pointer;
      color: var(--ev-text-muted);
      user-select: none;

      &:hover { color: var(--ev-primary); }
    }
  }
}
</style>
