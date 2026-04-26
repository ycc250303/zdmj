<route lang="yaml">
meta:
  constant: true
  hideInMenu: true
  layout: blank 
</route>
<script setup lang="ts">
interface Props {
  url: string;
}

defineProps<Props>();
</script>

<template>
  <div class="h-full">
    <iframe id="iframePage" class="size-full" :src="url"></iframe>
  </div>
</template>

<style scoped></style>
