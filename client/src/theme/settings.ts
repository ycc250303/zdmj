/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'light',
  grayscale: false,
  colourWeakness: false,
  recommendColor: false,
  themeColor: '#b86b4b',
  themeRadius: 12,
  otherColor: {
    info: '#5a8aa6',
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
        container: 'rgb(255, 252, 247)',
        layout: 'rgb(251, 248, 243)',
        inverted: 'rgb(38, 28, 22)',
        'base-text': 'rgb(45, 33, 26)'
      },
      boxShadow: {
        header: '0 1px 0 rgba(120, 80, 50, 0.08)',
        sider: '1px 0 0 rgba(120, 80, 50, 0.08)',
        tab: '0 1px 0 rgba(120, 80, 50, 0.08)'
      }
    },
    dark: {
      colors: {
        container: 'rgb(34, 28, 24)',
        layout: 'rgb(24, 20, 18)',
        'base-text': 'rgb(232, 224, 214)'
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
