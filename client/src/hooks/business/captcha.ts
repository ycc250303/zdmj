import { computed } from 'vue';
import { useCountDown, useLoading } from '@sa/hooks';
import { REG_PHONE } from '@/constants/reg';
import { $t } from '@/locales';
import { fetchGetVerificationCode } from '@/service/api/auth';

const REG_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function useCaptcha() {
  const { loading, startLoading, endLoading } = useLoading();
  const { count, start, stop, isCounting } = useCountDown(60);

  const label = computed(() => {
    let text = $t('page.login.codeLogin.getCode');

    const countingLabel = $t('page.login.codeLogin.reGetCode', { time: count.value });

    if (loading.value) {
      text = '';
    }

    if (isCounting.value) {
      text = countingLabel;
    }

    return text;
  });

 function isAccountValid(account: string) {
    if (account.trim() === '') {
      window.$message?.error?.('账号/邮箱不能为空');
      return false;
    }

    const isPhone = REG_PHONE.test(account);
    const isEmail = REG_EMAIL.test(account);

    if (!isPhone && !isEmail) {
      window.$message?.error?.('手机号或邮箱格式不正确');
      return false;
    }

    return true;
  }

  async function getCaptcha(account: string) {
    const valid = isAccountValid(account);

    if (!valid || loading.value) {
      return;
    }

    startLoading();
    const { error } = await fetchGetVerificationCode(account);
    if (error) {
      endLoading();
      return; 
    }
    if (!error) {
      window.$message?.success?.('验证码发送成功，请前往邮箱查收！');
      start(); 
    }
    endLoading();
  }

  return {
    label,
    start,
    stop,
    isCounting,
    loading,
    getCaptcha
  };
}
