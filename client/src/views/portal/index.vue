<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useThemeStore } from '@/store/modules/theme';
import { useAppStore } from '@/store/modules/app';
import { localStg } from '@/utils/storage';
import { $t } from '@/locales';

defineOptions({
  name: 'PortalIndex'
});

const router = useRouter();
const authStore = useAuthStore();
const themeStore = useThemeStore();
const appStore = useAppStore();

const isLogin = computed(() => Boolean(localStg.get('token')));
const themeColor = computed(() => themeStore.themeColor);

const langOptions = [
  { label: '中文', key: 'zh-CN' },
  { label: 'English', key: 'en-US' }
];

function handleLangSelect(key: string | number) {
  appStore.changeLocale(key as App.I18n.LangType); 
}

function handleAction() {
  if (isLogin.value) {
    router.push('/home');
  } else {
    router.push('/login');
  }
}
</script>

<template>
  <div class="relative h-screen w-full overflow-hidden bg-slate-50">
    
    <WaveBg :theme-color="themeColor" class="absolute-lt z-1 size-full" />

    <div class="relative z-10 h-full flex flex-col">
      
      <header class="h-16 bg-white/40 backdrop-blur-md border-b border-white/20 flex items-center justify-between px-8">
        <div class="text-2xl font-bold flex items-center gap-2">
          <NGradientText type="primary">
            {{ $t('page.portal.title') }}
          </NGradientText>
        </div>
        
        <div class="flex items-center gap-4">
          <!-- 调节明暗的按钮，但是调节的有问题，之后需要调整 -->
          <!-- <NButton quaternary circle @click="themeStore.toggleThemeScheme()">
            <template #icon>
              <icon-carbon-sun v-if="!themeStore.darkMode" class="text-xl text-slate-600 dark:text-slate-300" />
              <icon-carbon-moon v-else class="text-xl text-slate-600 dark:text-slate-300" />
            </template>
          </NButton> -->

          <NDropdown :options="langOptions" placement="bottom" @select="handleLangSelect">
            <NButton quaternary circle>
              <template #icon>
                <icon-carbon-language class="text-xl text-slate-600" />
              </template>
            </NButton>
          </NDropdown>

          <template v-if="isLogin">
            <div class="flex items-center gap-2 mr-4">
              <span class="text-slate-700 font-medium">
                {{ $t('page.portal.hello') }}{{ authStore.userInfo.userName }}
              </span>
            </div>
            <NButton type="primary" round @click="handleAction">
              {{ $t('page.portal.enterConsole') }}
            </NButton>
          </template>
          
          <template v-else>
            <NButton type="primary" size="large" round ghost @click="handleAction">
              {{ $t('page.portal.loginSystem') }}
            </NButton>
          </template>
        </div>
      </header>

      <main class="flex-1 flex flex-col items-center justify-center p-8 text-center">
        
        <div class="bg-white/60 backdrop-blur-lg rounded-3xl p-16 shadow-lg border border-white/50 max-w-4xl hover:shadow-xl transition-all duration-300">
          <NSpace vertical :size="32" align="center">
            
            <NGradientText type="primary" class="text-5xl font-black drop-shadow-sm whitespace-pre-line">
              {{ $t('page.portal.heroTitle') }}
            </NGradientText>
            
            <p class="text-xl text-slate-600 tracking-wide font-medium whitespace-pre-line">
              {{ $t('page.portal.heroDesc') }}
            </p>
            
            <div class="mt-8">
              <NButton 
                type="primary" 
                size="large" 
                round
                class="w-48 h-14 text-lg shadow-md hover:shadow-lg transition-all"
                @click="handleAction"
              >
                {{ isLogin ? $t('page.portal.actionEnter') : $t('page.portal.actionLogin') }}
              </NButton>
            </div>
            
          </NSpace>
        </div>
        
      </main>
    </div>
  </div>
</template>

<style scoped></style>