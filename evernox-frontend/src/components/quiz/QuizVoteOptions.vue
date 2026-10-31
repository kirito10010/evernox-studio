<template>
  <div class="quiz-vote">
    <div class="vote-options">
      <button
        v-for="(option, index) in options"
        :key="index"
        type="button"
        class="vote-option"
        :class="{
          selected: question.myVote === option,
          locked: !!question.myVote,
          removed: isRemoved(option),
        }"
        :disabled="disabled || !!question.myVote || isRemoved(option)"
        @click="emit('vote', option)"
      >
        <span class="vote-key">{{ keys[index] }}</span>
        <span class="vote-text">{{ option }}</span>
        <span v-if="isRemoved(option)" class="vote-removed-tag">已排除</span>
      </button>
    </div>
    <div v-if="question.myVote" class="vote-status">
      你选择了「{{ question.myVote }}」，{{ statusHint || '等待其他用户选择相同答案（连续 3 人一致即确认）' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuizQuestion } from '@/types/quiz'

const props = defineProps<{
  question: QuizQuestion
  disabled?: boolean
  /** 已被排除的选项：划线且不可选（喵问答使用） */
  removedOptions?: string[]
  /** 已投票后的提示文案 */
  statusHint?: string
}>()

const emit = defineEmits<{
  (e: 'vote', option: string): void
}>()

const keys = ['A', 'B', 'C', 'D']

const options = computed(() =>
  [props.question.optionA, props.question.optionB, props.question.optionC, props.question.optionD]
    .map((option) => (option || '').trim())
    .filter(Boolean)
)

const isRemoved = (option: string) => (props.removedOptions || []).includes(option)
</script>

<style scoped lang="scss">
.quiz-vote {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vote-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.vote-option {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  padding: 8px 9px;
  border: 1px solid var(--ev-border-subtle);
  border-radius: 9px;
  background: var(--ev-bg-surface);
  color: var(--ev-text-primary);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;

  &:hover:not(:disabled) {
    border-color: rgba(var(--ev-primary-rgb), 0.5);
    background: rgba(var(--ev-primary-rgb), 0.06);
    color: var(--ev-primary);
  }

  &.selected {
    border-color: var(--ev-primary);
    background: rgba(var(--ev-primary-rgb), 0.1);
    color: var(--ev-primary);
  }

  &.locked {
    cursor: default;
  }

  &.removed {
    cursor: not-allowed;
    color: var(--ev-text-muted);
    background: var(--ev-bg-tint);

    .vote-key {
      background: rgba(144, 147, 153, 0.14);
      color: var(--ev-text-muted);
    }

    .vote-text {
      text-decoration: line-through;
    }
  }

  &:disabled {
    opacity: 0.72;
  }
}

.vote-key {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  flex-shrink: 0;
  border-radius: 6px;
  background: rgba(var(--ev-primary-rgb), 0.1);
  font-size: 11px;
  font-weight: 700;
  color: var(--ev-primary);
}

.vote-text {
  min-width: 0;
  /* 右侧面板很窄：长选项换行显示，最多两行，避免被截断成看不懂的半句 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.45;
}

.vote-removed-tag {
  margin-left: auto;
  padding: 0 5px;
  border-radius: 4px;
  background: rgba(144, 147, 153, 0.16);
  color: var(--ev-text-muted);
  font-size: 10px;
  line-height: 16px;
  flex-shrink: 0;
}

.vote-status {
  font-size: 11.5px;
  color: var(--el-color-warning);
}

@media (max-width: 700px) {
  .vote-options {
    grid-template-columns: 1fr;
  }
}
</style>
