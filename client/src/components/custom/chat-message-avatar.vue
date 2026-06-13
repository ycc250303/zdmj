<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'ChatMessageAvatar'
});

const props = defineProps<{
  /** 1=user, 2=assistant */
  role: number;
}>();

const isAssistant = computed(() => props.role === 2);
</script>

<template>
  <div
    class="chat-message-avatar"
    :class="isAssistant ? 'chat-message-avatar--assistant' : 'chat-message-avatar--user'"
    aria-hidden="true"
  >
    <svg
      v-if="isAssistant"
      class="chat-message-avatar__icon"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <rect x="5" y="7" width="14" height="11" rx="3" stroke="currentColor" stroke-width="1.6" />
      <circle cx="9.5" cy="12" r="1.2" fill="currentColor" />
      <circle cx="14.5" cy="12" r="1.2" fill="currentColor" />
      <path d="M10 15.2c.8.7 2.2.7 3 0" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
      <path d="M9 7V5.8a3 3 0 016 0V7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      <path d="M4 10l-1.2-.7M20 10l1.2-.7" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
    </svg>
    <svg
      v-else
      class="chat-message-avatar__icon"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <circle cx="12" cy="8.2" r="3.2" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M6.5 18.5c1.2-2.8 3.4-4.3 5.5-4.3s4.3 1.5 5.5 4.3"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </svg>
  </div>
</template>

<style scoped>
.chat-message-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.chat-message-avatar__icon {
  width: 22px;
  height: 22px;
}

.chat-message-avatar--user {
  background: linear-gradient(145deg, #ff8a65 0%, #f4511e 100%);
  color: #fff7ed;
}

.chat-message-avatar--assistant {
  background: linear-gradient(145deg, #7c8cff 0%, #5b6af0 55%, #4f46e5 100%);
  color: #eef2ff;
}

.dark .chat-message-avatar--user {
  background: linear-gradient(145deg, #fb923c 0%, #ea580c 100%);
  color: #fff7ed;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.dark .chat-message-avatar--assistant {
  background: linear-gradient(145deg, #818cf8 0%, #6366f1 55%, #4f46e5 100%);
  color: #e0e7ff;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}
</style>
