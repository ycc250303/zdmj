// src/router/routes/modules/resumes.ts

export default {
  name: 'resumes',
  path: '/resumes',
  component: 'basic',
  meta: {
    title: '我的简历',
    i18nKey: 'route.resumes', 
    icon: 'mdi:briefcase-outline',
    order: 2
  },
  children: [
    {
      // 列表入口页
      name: 'resumes_index',
      path: '/resumes/index',
      component: 'self',
      meta: {
        title: '简历库',
        i18nKey: 'route.resumes',
        icon: 'mdi:file-document-multiple-outline'
      }
    },
    {
      // 编辑器工作台
      name: 'resumes_editor',
      path: '/resumes/editor',
      component: 'blank',
      meta: {
        title: '简历编辑器',
        i18nKey: 'route.resumesEditor',
        hideInMenu: true, 
        activeMenu: 'resumes_index' 
      }
    }
  ]
};