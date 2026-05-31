<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { useRouterPush } from '@/hooks/common/router';
import { $t } from '@/locales';

defineOptions({
  name: 'SiderFooter'
});

const route = useRoute();
const appStore = useAppStore();
const themeStore = useThemeStore();
const { routerPushByKey } = useRouterPush();

const isActive = computed(() => route.name === 'user-settings');

const inverted = computed(() => !themeStore.darkMode && themeStore.sider.inverted);

function goToSettings() {
  routerPushByKey('user-settings');
}
</script>

<template>
  <div
    class="shrink-0 border-t border-gray-200/60 px-8px py-8px dark:border-white/10"
    :class="{ 'px-4px': appStore.siderCollapse }"
  >
    <NTooltip v-if="appStore.siderCollapse" placement="right">
      <template #trigger>
        <NButton
          quaternary
          block
          :type="isActive ? 'primary' : 'default'"
          :class="{ 'text-white:88 !hover:text-white': inverted && !isActive }"
          @click="goToSettings"
        >
          <template #icon>
            <SvgIcon icon="mdi:cog-outline" class="text-20px" />
          </template>
        </NButton>
      </template>
      {{ $t('page.userSettings.navTitle') }}
    </NTooltip>

    <NButton
      v-else
      quaternary
      block
      :type="isActive ? 'primary' : 'default'"
      class="justify-start"
      :class="{ 'text-white:88 !hover:text-white': inverted && !isActive }"
      @click="goToSettings"
    >
      <template #icon>
        <SvgIcon icon="mdi:cog-outline" class="text-20px" />
      </template>
      {{ $t('page.userSettings.navTitle') }}
    </NButton>
  </div>
</template>
