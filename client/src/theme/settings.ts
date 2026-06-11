/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'light',
  grayscale: false,
  colourWeakness: false,
  recommendColor: false,
  themeColor: '#c4a46c',
  themeRadius: 8,
  otherColor: {
    info: '#222222',
    success: '#3f8a6a',
    warning: '#d7a13b',
    error: '#c44536'
  },
  isInfoFollowPrimary: true,
  layout: {
    mode: 'vertical',
    scrollMode: 'content'
  },
  page: {
    animate: true,
    animateMode: 'fade-slide'
  },
  header: {
    height: 56,
    breadcrumb: {
      visible: true,
      showIcon: true
    },
    multilingual: {
      visible: true
    },
    globalSearch: {
      visible: true
    }
  },
  tab: {
    visible: true,
    cache: true,
    height: 44,
    mode: 'chrome',
    closeTabByMiddleClick: false
  },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 220,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: {
    visible: true,
    fixed: false,
    height: 48,
    right: true
  },
  watermark: {
    visible: false,
    text: 'SoybeanAdmin',
    enableUserName: false,
    enableTime: false,
    timeFormat: 'YYYY-MM-DD HH:mm'
  },
  tokens: {
    light: {
      colors: {
        container: 'rgb(255, 255, 255)',
        layout: 'rgb(247, 247, 247)',
        inverted: 'rgb(34, 34, 34)',
        'base-text': 'rgb(63, 63, 63)'
      },
      boxShadow: {
        header: '0 1px 0 rgba(0, 0, 0, 0.06)',
        sider: '1px 0 0 rgba(0, 0, 0, 0.06)',
        tab: '0 1px 0 rgba(0, 0, 0, 0.06)'
      }
    },
    dark: {
      colors: {
        container: 'rgb(24, 24, 24)',
        layout: 'rgb(18, 18, 18)',
        'base-text': 'rgb(210, 210, 210)'
      }
    }
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {};
