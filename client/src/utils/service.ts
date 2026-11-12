/**
 * Create service config by current env
 *
 * @param env The current env
 */
export function createServiceConfig(env: Env.ImportMeta) {
  const config: App.Service.ServiceConfigItem = {
    baseURL: env.VITE_SERVICE_BASE_URL,
    proxyPattern: '/proxy-default'
  };

  return config;
}

/**
 * get backend service base url
 *
 * @param env - the current env
 * @param isProxy - if use proxy
 */
export function getServiceBaseURL(env: Env.ImportMeta, isProxy: boolean) {
  const { baseURL, proxyPattern } = createServiceConfig(env);
  return isProxy ? proxyPattern : baseURL;
}
