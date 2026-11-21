<template>
  <div class="phrase-page">
    <header class="page-head">
      <div>
        <h2 class="page-title">老话辨正</h2>
        <p class="page-sub">
          先说原句，再说原意，最后看它被改成了什么。共 {{ total }} 条，越常用越靠前。
        </p>
      </div>
      <span class="head-mark">一字之差 / 断章取义 / 词义反转 / 假托名人</span>
    </header>

    <div class="layout">
      <nav class="index">
        <button
          v-for="item in PHRASE_CATEGORIES"
          :key="item.key"
          type="button"
          class="index-item"
          :class="[{ active: activeCategory === item.key }, `cat-${item.key}`]"
          @click="activeCategory = item.key"
        >
          <i class="dot" />
          <span class="name">{{ item.label }}</span>
          <span class="count">{{ counts[item.key] }}</span>
        </button>

        <p class="index-note">点条目展开，可以看到原句、原意和现在被理解成的意思。</p>
      </nav>

      <section class="content">
        <div class="toolbar">
          <label class="search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" type="text" placeholder="搜索说法、原句、原意…" />
            <button v-if="keyword" type="button" class="clear" @click="keyword = ''">
              <el-icon><Close /></el-icon>
            </button>
          </label>
          <span class="result">{{ filtered.length }} 条</span>
          <button type="button" class="toggle-all" @click="toggleAll">
            {{ allExpanded ? '全部收起' : '全部展开' }}
          </button>
        </div>

        <div v-if="!filtered.length" class="empty">
          <p>没有匹配的条目</p>
          <button type="button" class="empty-btn" @click="reset">清空筛选</button>
        </div>

        <template v-else>
          <div class="list-head">
            <i class="gdot" :class="`cat-${activeMeta.key}`" />
            <h3>{{ activeMeta.label }}</h3>
            <span class="list-desc">{{ activeMeta.desc }}</span>
            <span class="list-count">{{ filtered.length }} 条</span>
          </div>

          <div
            v-for="(entry, index) in filtered"
            :key="entry.id"
            class="entry"
            :class="{ open: isOpen(entry.id) }"
            role="button"
            tabindex="0"
            :aria-expanded="isOpen(entry.id)"
            @click="toggle(entry.id)"
            @keydown.enter.prevent="toggle(entry.id)"
            @keydown.space.prevent="toggle(entry.id)"
          >
            <span class="no">{{ String(index + 1).padStart(2, '0') }}</span>

            <div class="entry-text">
              <div class="saying-row">
                <span class="saying">「{{ entry.saying }}」</span>
                <span
                  v-if="activeCategory === 'all'"
                  class="cat-tag"
                  :class="`cat-${entry.category}`"
                >
                  {{ TAG_LABEL[entry.category] }}
                </span>
              </div>
              <p class="original">{{ entry.original }}</p>
            </div>

            <el-icon class="chev"><ArrowDown /></el-icon>

            <div class="detail">
              <dl>
                <div>
                  <dt>原意</dt>
                  <dd>{{ entry.meaning }}</dd>
                </div>
                <div>
                  <dt>现在被理解成</dt>
                  <dd class="distortion">{{ entry.distortion }}</dd>
                </div>
                <div v-if="entry.note">
                  <dt>补充</dt>
                  <dd>{{ entry.note }}</dd>
                </div>
                <div v-if="entry.source">
                  <dt>出处</dt>
                  <dd class="source">{{ entry.source }}</dd>
                </div>
              </dl>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  POPULARITY_ORDER,
  PHRASE_CATEGORIES,
  PHRASE_ENTRIES,
} from '@/data/phraseChecks'
import type { CategoryKey, PhraseCategory } from '@/data/phraseChecks'

const TAG_LABEL: Record<PhraseCategory, string> = {
  homophone: '谐音',
  truncated: '截断',
  reversed: '反义',
  attributed: '伪托',
}

const rankOf = new Map(POPULARITY_ORDER.map((id, index) => [id, index]))

const total = PHRASE_ENTRIES.length
const activeCategory = ref<CategoryKey>('all')
const keyword = ref('')
const expanded = ref<number[]>([])

const activeMeta = computed(
  () => PHRASE_CATEGORIES.find((item) => item.key === activeCategory.value) ?? PHRASE_CATEGORIES[0]
)

const counts = computed<Record<CategoryKey, number>>(() => {
  const map: Record<CategoryKey, number> = {
    all: total,
    homophone: 0,
    truncated: 0,
    reversed: 0,
    attributed: 0,
  }
  for (const entry of PHRASE_ENTRIES) {
    map[entry.category] += 1
  }
  return map
})

const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return PHRASE_ENTRIES.filter((entry) => {
    if (activeCategory.value !== 'all' && entry.category !== activeCategory.value) return false
    if (!word) return true
    return [
      entry.saying,
      entry.original,
      entry.meaning,
      entry.distortion,
      entry.note ?? '',
      entry.source ?? '',
    ]
      .join(' ')
      .toLowerCase()
      .includes(word)
  }).sort((a, b) => (rankOf.get(a.id) ?? 0) - (rankOf.get(b.id) ?? 0))
})

const isOpen = (id: number): boolean => expanded.value.includes(id)

const toggle = (id: number) => {
  const index = expanded.value.indexOf(id)
  if (index >= 0) expanded.value.splice(index, 1)
  else expanded.value.push(id)
}

const allExpanded = computed(
  () =>
    filtered.value.length > 0 &&
    filtered.value.every((entry) => expanded.value.includes(entry.id))
)

const toggleAll = () => {
  if (allExpanded.value) {
    const visible = new Set(filtered.value.map((entry) => entry.id))
    expanded.value = expanded.value.filter((id) => !visible.has(id))
    return
  }
  const merged = new Set([...expanded.value, ...filtered.value.map((entry) => entry.id)])
  expanded.value = [...merged]
}

const reset = () => {
  activeCategory.value = 'all'
  keyword.value = ''
}
</script>

<style scoped lang="scss">
.phrase-page {
  max-width: 1160px;
  margin: 0 auto;
  font-variant-numeric: tabular-nums;

  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    gap: 20px;
    padding-bottom: 14px;
    margin-bottom: 20px;
    border-bottom: 1px solid var(--ev-border-subtle);

    .page-title {
      margin: 0 0 6px;
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: var(--ev-text-primary);
    }

    .page-sub {
      margin: 0;
      font-size: 12.5px;
      color: var(--ev-text-muted);
    }

    .head-mark {
      flex-shrink: 0;
      font-size: 11px;
      letter-spacing: 0.08em;
      color: var(--ev-text-muted);
    }
  }

  .layout {
    display: grid;
    grid-template-columns: 176px minmax(0, 1fr);
    gap: 24px;
    align-items: start;
  }

  // ---------------- 左索引 ----------------
  .index {
    position: sticky;
    top: 16px;
    display: flex;
    flex-direction: column;
    gap: 2px;

    .index-item {
      display: flex;
      align-items: center;
      gap: 9px;
      padding: 8px 10px;
      border: none;
      border-radius: 7px;
      background: transparent;
      font-size: 13px;
      color: var(--ev-text-secondary);
      text-align: left;
      cursor: pointer;
      transition: background 0.15s var(--ev-ease-out), color 0.15s var(--ev-ease-out);

      .dot {
        flex-shrink: 0;
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #b9c6d4;
      }

      .name {
        flex: 1;
      }

      .count {
        font-size: 11px;
        color: var(--ev-text-muted);
      }

      &:hover {
        background: var(--ev-bg-tint-strong);
        color: var(--ev-text-primary);
      }

      &.active {
        background: var(--ev-bg-tint-strong);
        font-weight: 600;
        color: var(--ev-text-primary);

        .count {
          color: var(--ev-text-secondary);
        }
      }

      &.cat-homophone .dot {
        background: #45a08b;
      }

      &.cat-truncated .dot {
        background: #5b93c7;
      }

      &.cat-reversed .dot {
        background: #8b7fd4;
      }

      &.cat-attributed .dot {
        background: #a87a1c;
      }
    }

    .index-note {
      margin: 18px 0 0;
      padding-top: 12px;
      border-top: 1px solid var(--ev-border-subtle);
      font-size: 11px;
      line-height: 1.7;
      color: var(--ev-text-muted);
    }
  }

  // ---------------- 正文面板 ----------------
  .content {
    padding: 16px 22px 26px;
    border-radius: var(--ev-radius-lg);
    background: var(--ev-bg-elevated);
    border: 1px solid var(--ev-border-subtle);
    box-shadow: var(--ev-shadow-card);
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 14px;
    padding-bottom: 14px;
    border-bottom: 1px solid var(--ev-border-subtle);
    margin-bottom: 6px;
  }

  .search {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    padding: 0 11px;
    height: 36px;
    border-radius: 8px;
    border: 1px solid var(--ev-border-default);
    background: var(--ev-bg-surface);
    color: var(--ev-text-muted);
    transition: border-color 0.15s var(--ev-ease-out);

    &:focus-within {
      border-color: var(--ev-border-active);
      background: var(--ev-bg-elevated);
    }

    input {
      flex: 1;
      min-width: 0;
      border: none;
      outline: none;
      background: transparent;
      font-size: 13px;
      color: var(--ev-text-primary);

      &::placeholder {
        color: var(--ev-text-muted);
      }
    }

    .clear {
      display: inline-flex;
      padding: 2px;
      border: none;
      border-radius: 4px;
      background: transparent;
      color: var(--ev-text-muted);
      cursor: pointer;

      &:hover {
        color: var(--ev-text-secondary);
        background: var(--ev-bg-tint);
      }
    }
  }

  .result {
    font-size: 12px;
    color: var(--ev-text-muted);
    white-space: nowrap;
  }

  .toggle-all {
    padding: 6px 11px;
    border: 1px solid var(--ev-border-default);
    border-radius: 7px;
    background: transparent;
    font-size: 12px;
    color: var(--ev-text-secondary);
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.15s var(--ev-ease-out);

    &:hover {
      color: var(--ev-primary);
      border-color: var(--ev-border-hover);
      background: var(--ev-bg-tint);
    }
  }

  // ---------------- 列表头 ----------------
  .list-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 16px;
    padding-bottom: 9px;
    border-bottom: 1px solid var(--ev-border-default);

    .gdot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #b9c6d4;

      &.cat-homophone {
        background: #45a08b;
      }

      &.cat-truncated {
        background: #5b93c7;
      }

      &.cat-reversed {
        background: #8b7fd4;
      }

      &.cat-attributed {
        background: #a87a1c;
      }
    }

    h3 {
      margin: 0;
      font-size: 13.5px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: var(--ev-text-primary);
    }

    .list-desc {
      flex: 1;
      font-size: 11.5px;
      color: var(--ev-text-muted);
    }

    .list-count {
      font-size: 11.5px;
      color: var(--ev-text-muted);
    }
  }

  // ---------------- 条目 ----------------
  .entry {
    position: relative;
    display: grid;
    grid-template-columns: 26px minmax(0, 1fr) 14px;
    column-gap: 10px;
    padding: 13px 6px;
    border-bottom: 1px solid var(--ev-border-subtle);
    border-radius: 6px;
    cursor: pointer;
    outline: none;
    transition: background 0.15s var(--ev-ease-out);

    &:hover {
      background: rgba(var(--ev-primary-rgb), 0.045);
    }

    &:focus-visible {
      box-shadow: inset 0 0 0 1px var(--ev-border-active);
    }

    .no {
      padding-top: 3px;
      font-size: 11.5px;
      font-weight: 500;
      color: var(--ev-text-muted);
      letter-spacing: 0.04em;
      transition: color 0.15s var(--ev-ease-out);
    }

    &:hover .no {
      color: var(--ev-primary);
    }

    .saying-row {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 8px;
    }

    .saying {
      font-size: 15.5px;
      font-weight: 600;
      line-height: 1.5;
      letter-spacing: 0.01em;
      color: var(--ev-text-primary);
    }

    .cat-tag {
      padding: 0 5px;
      border: 1px solid currentColor;
      border-radius: 3px;
      font-size: 10px;
      line-height: 15px;
      opacity: 0.6;

      &.cat-homophone {
        color: #45a08b;
      }

      &.cat-truncated {
        color: #5b93c7;
      }

      &.cat-reversed {
        color: #8b7fd4;
      }

      &.cat-attributed {
        color: #a87a1c;
      }
    }

    .original {
      margin: 5px 0 0;
      font-size: 13.5px;
      line-height: 1.75;
      color: var(--ev-text-secondary);
    }

    .chev {
      align-self: start;
      margin-top: 6px;
      font-size: 12px;
      color: var(--ev-text-muted);
      transition: transform 0.22s var(--ev-ease-out);
    }

    &.open .chev {
      transform: rotate(180deg);
    }

    // 展开区
    .detail {
      grid-column: 1 / -1;
      display: grid;
      grid-template-rows: 0fr;
      opacity: 0;
      transition: grid-template-rows 0.26s var(--ev-ease-out), opacity 0.2s var(--ev-ease-out);

      dl {
        overflow: hidden;
        margin: 0;
        padding: 0 2px;
      }

      dl > div {
        display: grid;
        grid-template-columns: 92px minmax(0, 1fr);
        gap: 12px;
        padding-top: 9px;
      }

      dt {
        font-size: 11.5px;
        color: var(--ev-text-muted);
      }

      dd {
        margin: 0;
        font-size: 12.5px;
        line-height: 1.75;
        color: var(--ev-text-secondary);
      }

      .distortion {
        color: #b8790a;
      }

      .source {
        color: var(--ev-text-muted);
      }
    }

    &.open .detail {
      grid-template-rows: 1fr;
      opacity: 1;

      dl {
        padding-bottom: 5px;
      }
    }
  }

  // ---------------- 空结果 ----------------
  .empty {
    padding: 60px 0;
    text-align: center;

    p {
      margin: 0 0 12px;
      font-size: 13px;
      color: var(--ev-text-muted);
    }

    .empty-btn {
      padding: 6px 14px;
      border: 1px solid var(--ev-border-default);
      border-radius: 7px;
      background: transparent;
      font-size: 12.5px;
      color: var(--ev-text-secondary);
      cursor: pointer;

      &:hover {
        color: var(--ev-primary);
        border-color: var(--ev-border-active);
      }
    }
  }

  @media (max-width: 900px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
      gap: 14px;
    }

    .index {
      position: static;
      flex-direction: row;
      overflow-x: auto;
      padding-bottom: 4px;
      gap: 6px;

      .index-item {
        flex-shrink: 0;
        padding: 6px 10px;
        border: 1px solid var(--ev-border-subtle);
        background: var(--ev-bg-elevated);
      }

      .index-note {
        display: none;
      }
    }

    .content {
      padding: 14px 16px 20px;
    }
  }

  @media (max-width: 640px) {
    .page-head {
      flex-direction: column;
      align-items: flex-start;
      gap: 8px;

      .head-mark {
        display: none;
      }
    }

    .toolbar {
      flex-wrap: wrap;
      gap: 8px;
    }

    .search {
      flex-basis: 100%;
    }

    .entry {
      grid-template-columns: 22px minmax(0, 1fr) 14px;
      column-gap: 8px;
      padding: 12px 4px;

      .saying {
        font-size: 14.5px;
      }

      .original {
        font-size: 13px;
      }

      .detail dl > div {
        grid-template-columns: minmax(0, 1fr);
        gap: 2px;
      }
    }
  }
}
</style>
