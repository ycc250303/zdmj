# 客户端文件夹

使用 Node.js ≥20.19 与 pnpm ≥10.5 安装依赖：

```sh
pnpm install
```

聊天消息统一经过 Markdown 解析、代码高亮和 DOMPurify 清理后展示。用户消息、流式累计正文和历史消息共用该入口；数据库保留消息原文。

安全渲染测试通过 Vitest 在 Chromium 中执行。首次运行前安装浏览器：

```sh
pnpm exec playwright install chromium --no-shell
pnpm test
```

检查类型、代码规范与生产构建：

```sh
pnpm typecheck
pnpm lint
pnpm build
```
