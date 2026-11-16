import { createApp, defineComponent, h, nextTick, ref } from 'vue';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/core';
import { Lexer } from 'marked';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderMessageMarkdown } from './markdown';

const containers: HTMLElement[] = [];
const unmounts: (() => void)[] = [];

function display(content: string) {
  const container = document.createElement('div');
  document.body.append(container);
  containers.push(container);
  container.innerHTML = renderMessageMarkdown(content);
  return container;
}

afterEach(() => {
  unmounts.splice(0).forEach(unmount => unmount());
  containers.splice(0).forEach(container => container.remove());
  vi.restoreAllMocks();
});

describe('聊天 Markdown 安全渲染', () => {
  it.each([
    '<img src="data:image/png;base64,invalid" onerror="window.alert(1)">',
    '<a href="#" onclick="window.alert(1)">点击</a>',
    '<svg onload="window.alert(1)"><a href="javascript:alert(1)">点击</a></svg>',
    '<math><mtext><img src="data:image/png;base64,invalid" onerror="window.alert(1)"></mtext></math>',
    '<script>window.alert(1)</script>',
    '<iframe srcdoc="<script>parent.alert(1)</script>"></iframe>',
    '<object data="javascript:alert(1)"></object><embed src="javascript:alert(1)">',
    '<form id="chat"><input name="content"></form>',
    '<style>body { display: none }</style><p id="chat" name="content" style="position:fixed" onclick="alert(1)">正文</p>'
  ])('清理危险节点和属性：%s', async content => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const container = display(content);

    container.querySelectorAll('*').forEach(element => {
      expect(element.getAttributeNames().filter(name => /^on/i.test(name))).toEqual([]);
      expect(element.hasAttribute('style')).toBe(false);
      expect(element.hasAttribute('id')).toBe(false);
      expect(element.hasAttribute('name')).toBe(false);
      element.dispatchEvent(new Event('error'));
      element.dispatchEvent(new Event('load'));
      element.dispatchEvent(new MouseEvent('click'));
    });
    expect(container.querySelector('script, svg, math, iframe, object, embed, form, style')).toBeNull();
    await new Promise(resolve => {
      window.setTimeout(resolve, 20);
    });
    expect(alert).not.toHaveBeenCalled();
  });

  it.each([
    '[点击](javascript:alert%281%29)',
    '<a href="jav&#x61;script:alert(1)">点击</a>',
    '<a href="java&#10;script:alert(1)">点击</a>',
    '<a href="vbscript:msgbox(1)">点击</a>',
    '<a href="data:text/html,<script>alert(1)</script>">点击</a>'
  ])('移除危险链接协议：%s', content => {
    const link = display(content).querySelector('a');
    expect(link).not.toBeNull();
    expect(link?.hasAttribute('href')).toBe(false);
  });

  it('保留正文、表格、链接、图片、任务列表和代码高亮', () => {
    const container = display(
      [
        '# 标题',
        '**重点**与*强调*\n下一行',
        '> 引用',
        '- 普通列表\n- [x] 已完成',
        '| 技能 | 熟练程度 |\n| --- | --- |\n| Java | 熟练 |',
        '[链接](https://example.com "说明")',
        '![图片](data:image/png;base64,iVBORw0KGgo= "图片说明")',
        '```javascript\nconst answer = 42;\n```'
      ].join('\n\n')
    );

    expect(container.querySelector('h1')?.textContent).toBe('标题');
    expect(container.querySelector('strong')?.textContent).toBe('重点');
    expect(container.querySelector('em')?.textContent).toBe('强调');
    expect(container.querySelector('br')).not.toBeNull();
    expect(container.querySelector('blockquote')?.textContent).toContain('引用');
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.querySelector<HTMLInputElement>('input[type="checkbox"]')?.disabled).toBe(true);
    expect(container.querySelector('table td')?.textContent).toBe('Java');
    expect(container.querySelector('a')?.getAttribute('href')).toBe('https://example.com');
    expect(container.querySelector('img')?.alt).toBe('图片');
    expect(container.querySelector('code.hljs.language-javascript .hljs-keyword')?.textContent).toBe('const');
  });

  it.each(['', 'unknown-language', 'html'])('代码围栏安全展示原文，语言为 %s', language => {
    const code = '<img src="data:image/png;base64,invalid" onerror="alert(1)"> & <script>alert(1)</script>';
    const container = display(`\`\`\`${language}\n${code}\n\`\`\``);
    expect(container.querySelector('code')?.textContent).toBe(code);
    expect(container.querySelector('img, script')).toBeNull();
  });

  it('空内容返回空字符串', () => {
    expect(renderMessageMarkdown('')).toBe('');
  });

  it('清理异常立即传递，不返回原始消息', () => {
    const failure = new Error('清理失败');
    vi.spyOn(DOMPurify, 'sanitize').mockImplementationOnce(() => {
      throw failure;
    });
    expect(() => renderMessageMarkdown('<img onerror="alert(1)">')).toThrow(failure);
  });

  it('Markdown 解析异常立即传递，不返回原始消息', () => {
    const failure = new Error('解析失败');
    vi.spyOn(Lexer, 'lex').mockImplementationOnce(() => {
      throw failure;
    });
    expect(() => renderMessageMarkdown('<img onerror="alert(1)">')).toThrow(failure);
  });

  it('代码高亮异常立即传递，不返回原始消息', () => {
    const failure = new Error('高亮失败');
    vi.spyOn(hljs, 'highlight').mockImplementationOnce(() => {
      throw failure;
    });
    expect(() => renderMessageMarkdown('```javascript\nalert(1)\n```')).toThrow(failure);
  });

  it('用户消息、流式累计正文和重新加载的历史消息使用相同安全渲染', async () => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const messages = ref([
      { role: 1, content: '<img src="data:image/png;base64,invalid" onerror="alert(1)">' },
      { role: 2, content: '' }
    ]);
    const container = document.createElement('div');
    document.body.append(container);
    containers.push(container);
    const app = createApp(
      defineComponent({
        setup: () => () =>
          h(
            'div',
            messages.value.map(message =>
              h('div', {
                'data-role': message.role,
                innerHTML: renderMessageMarkdown(message.content)
              })
            )
          )
      })
    );
    app.mount(container);
    unmounts.push(() => app.unmount());

    const chunks = [
      '<img src="data:image/png;base64,invalid" on',
      'error="alert(1)">',
      '\n\n[点击](java',
      'script:alert%281%29)'
    ];
    // 逐次等待 Vue 更新，覆盖分片尚未闭合时的真实 DOM。
    for (const chunk of chunks) {
      messages.value[1].content += chunk;
      // eslint-disable-next-line no-await-in-loop
      await nextTick();
      expect(container.querySelector('[onerror], a[href^="javascript:"]')).toBeNull();
      container.querySelectorAll('img').forEach(image => image.dispatchEvent(new Event('error')));
    }

    const completedHtml = container.innerHTML;
    messages.value = JSON.parse(JSON.stringify(messages.value));
    await nextTick();
    expect(container.innerHTML).toBe(completedHtml);
    expect(alert).not.toHaveBeenCalled();
  });
});
