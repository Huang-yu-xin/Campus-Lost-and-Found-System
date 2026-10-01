// 后端 API 基址。开发默认本机；真机/正式需配置合法 HTTPS 域名并在微信后台加入 request 合法域名。
// P1-F6：按构建环境区分 dev / prod。uni-app `build:mp-weixin` 生产构建时 NODE_ENV=production，
// 取 API_BASE_PROD；开发构建行为保持不变（localhost）。
const API_BASE_DEV = 'http://localhost:8080/api/v1'

// 生产占位：部署前替换为真实 HTTPS 域名，并在微信公众平台「开发管理 → 服务器域名 → request 合法域名」
// 中加入该域名（微信小程序只允许 https 且域名须备案+配置白名单，不能用 IP/localhost）。
const API_BASE_PROD = 'https://REPLACE_WITH_YOUR_DOMAIN/api/v1'

export const API_BASE = process.env.NODE_ENV === 'production' ? API_BASE_PROD : API_BASE_DEV
