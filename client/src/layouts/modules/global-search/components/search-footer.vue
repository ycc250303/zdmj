<script lang="ts" setup>
import { $t } from '@/locales';

defineOptions({ name: 'SearchFooter' });
</script>

<template>
  <div class="h-44px flex-y-center gap-14px px-24px">
    <span class="flex-y-center">
      <span class="operate-shadow operate-item">↵</span>
      <span>{{ $t('common.confirm') }}</span>
    </span>
    <span class="flex-y-center">
      <span class="operate-shadow operate-item">↑</span>
      <span class="operate-shadow operate-item">↓</span>
      <span>{{ $t('common.switch') }}</span>
    </span>
    <span class="flex-y-center">
      <span class="operate-shadow operate-item">Esc</span>
      <span>{{ $t('common.close') }}</span>
    </span>
  </div>
</template>

<style lang="scss" scoped>
.operate-shadow {
  box-shadow:
    inset 0 -2px #cdcde6,
    inset 0 0 1px 1px #fff,
    0 1px 2px 1px #1e235a66;
}

.operate-item {
  --uno: mr-6px p-2px text-20px;
}
</style>
