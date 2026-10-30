<template>
  <div class="quiz-vote">
    <div class="vote-options">
      <button
        v-for="(option, index) in options"
        :key="index"
        type="button"
        class="vote-option"
        :class="{ selected: question.myVote === option, locked: !!question.myVote }"
        :disabled="disabled || !!question.myVote"
        @click="emit('vote', option)"
      >
        <span class="vote-key">{{ keys[index] }}</span>
        <span class="vote-text">{{ option }}</span>
      </button>
    </div>
    <div v-if="question.myVote" class="vote-status">
      你选择了「{{ question.myVote }}」，等待另一位用户选择相同答案
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuizQuestion } from '@/types/quiz'

const props = defineProps<{
  question: QuizQuestion
  disabled?: boolean
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
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 8px 10px;
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

  &:disabled {
    opacity: 0.72;
  }
}

.vote-key {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 6px;
  background: rgba(var(--ev-primary-rgb), 0.1);
  font-size: 11px;
  font-weight: 700;
  color: var(--ev-primary);
}

.vote-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
