import process from 'node:process';
import path from 'node:path';
import { createRequire } from 'node:module';
import unocss from '@unocss/vite';
import presetIcons from '@unocss/preset-icons';
import { FileSystemIconLoader } from '@iconify/utils/lib/loader/node-loaders';

const require = createRequire(import.meta.url);

/**
 * 从 @iconify/json 全量包中加载指定图标集
 * 项目使用 @iconify/json 而非 @iconify-json/<set> 形式，
 * 因此 preset-icons 默认的自动发现机制不生效，需要手动注册。
 */
function loadIconifyCollection(name: string) {
  return async () => {
    try {
      const json = require(`@iconify/json/json/${name}.json`);
      return json;
    } catch (e) {
      console.warn(`[unocss] 未找到图标集 @iconify/json/json/${name}.json`);
      return undefined;
    }
  };
}

export function setupUnocss(viteEnv: Env.ImportMeta) {
  const { VITE_ICON_PREFIX, VITE_ICON_LOCAL_PREFIX } = viteEnv;

  const localIconPath = path.join(process.cwd(), 'src/assets/svg-icon');

  /** The name of the local icon collection */
  const collectionName = VITE_ICON_LOCAL_PREFIX.replace(`${VITE_ICON_PREFIX}-`, '');

  return unocss({
    presets: [
      presetIcons({
        prefix: `${VITE_ICON_PREFIX}-`,
        scale: 1,
        extraProperties: {
          display: 'inline-block'
        },
        collections: {
          // 本地 svg 图标集
          [collectionName]: FileSystemIconLoader(localIconPath, svg =>
            svg.replace(/^<svg\s/, '<svg width="1em" height="1em" ')
          ),
          // 远程图标集（从 @iconify/json 全量包按需加载）
          mdi: loadIconifyCollection('mdi'),
          'icon-park-outline': loadIconifyCollection('icon-park-outline'),
          carbon: loadIconifyCollection('carbon'),
          ph: loadIconifyCollection('ph'),
          tabler: loadIconifyCollection('tabler')
        },
        warn: true
      })
    ]
  });
}
