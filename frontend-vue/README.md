# LuminaWalk 前端迁移

完整迁移自 [Qulamb/CityAIHub 的 8e1b70e 提交](https://github.com/Qulamb/CityAIHub/tree/8e1b70e2713cdc46ceba3f2793b6fe1f6d86cf10/frontend-vue)，采用 Vue 3、Vite、Element Plus、Axios 和 Pinia。

本目录包含该提交的全部 59 个前端文件，包括首页、分类页、商家详情、个人中心、AI 助手、登录组件、API 模块、状态管理、模拟数据及 20 个 SVG 图片资源。保留上游页面文案、业务实现和依赖锁定版本，尚未进行摄影场景的界面与内容改造。

## 启动

```sh
cd /Users/nicoleguo/Documents/CodexProject/LuminaWalk/frontend-vue
npm ci
npm run dev
```

访问 <http://localhost:5173>。开发请求的 `/api` 前缀由 Vite 移除后转发到本地后端 <http://localhost:8081>；使用业务数据需要先启动后端及其依赖服务。

## 构建与预览

```sh
npm run build
npm run preview
```

构建产物仅写入 `frontend-vue/dist/`，不会清空或写入后端的 `src/main/resources/static/`。为兼容上游命令，保留了 `npm run build:backend`，它同样只生成本目录的 `dist/`。

`preview` 用于检查构建后的静态页面；生产代码沿用上游的同源 API 请求方式，实际部署时需要将页面与后端接口配置在同一域名下。部署、后端静态资源复制和反向代理配置均不包含在本次迁移中。

所有 npm 命令显式使用 `vite.config.cjs`。上游另有一份 `vite.config.js`，本次也保留，并同步了输出目录和后端端口。

## 本地适配

- 两份 Vite 配置的构建输出改为本目录的 `dist/`，开发代理端口从 `8083` 改为 `8081`。
- 同步 API 请求文件中的端口注释。
- 更新本说明，并在本目录添加 `.gitignore`，忽略依赖、构建产物和本地缓存。
- 其余上游前端文件保持原始内容，未迁移该提交的 Java、SQL、后端构建产物或仓库根目录文件。

## 当前联调限制

上游 README 中“全部使用 mock、不依赖后端”的描述已经不符合此提交的代码：首页、登录、优惠券等会调用真实接口；部分图片、评论和 AI 失败后的降级回答仍使用模拟数据。

与本地后端代码核对后，已确认以下接口差异：

| 前端调用 | 当前本地后端 |
| --- | --- |
| `POST /ai/agent/conversations` 创建会话 | 仅有该路径的 GET 查询接口，没有独立创建接口 |
| `GET /ai/agent/conversations/{id}/stream` 订阅 SSE | 没有该接口；当前通过 `POST /ai/agent/chat` 返回 SSE |
| 聊天请求使用 `content`，期望提交后通过独立流或消息查询获取回答 | 聊天 DTO 使用 `prompt`，且该 POST 直接返回事件流 |
| `POST /voucher-order/buy/{voucherId}` 购买普通券 | 当前 Controller 仅提供秒杀下单接口 |

这些差异未通过修改后端或重写前端业务流程来消除。前端安装、构建和页面展示的检查，并不表示 AI 对话、普通券购买或支付等业务已经完成端到端联调；AI 的模拟降级回答也不代表真实模型调用成功。

## 迁移验证

- 59 个上游文件完整迁入，无空文件；55 个文件的 Git blob 哈希与上游一致，4 个文件为上文列出的说明、注释及配置适配，另新增本目录的 `.gitignore`。
- `npm ci --no-audit --no-fund` 和 `npm run build` 通过，依赖锁文件保持上游内容；构建存在上游依赖的 Sass legacy JS API 弃用提示。
- 在浏览器检查了首页、分类页、个人中心的未登录状态、AI 助手页以及登录弹窗的渲染。
- 验证时后端连接不可用，业务数据未加载；分类页的上游实现还会因未捕获请求失败而输出 mounted hook 警告，该问题本次保留。
- 比较迁移前后的文件哈希，确认 `frontend-vue` 以外的工作文件内容没有变化。
