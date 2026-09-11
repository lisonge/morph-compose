# 项目协作规则

- `morph-playground` 的 Kotlin/Wasm 目标仅用于浏览器演示。除非用户明确要求，不执行浏览器端运行测试、截图测试或自动化测试。
- 涉及算法、Compose UI 或共享状态的改动，优先通过 JVM 单元测试和 Desktop/JVM 应用实跑验证。
- 修改共享代码时应保持 Wasm 目标可编译；不要求为 Wasm 建立与 JVM 对等的测试矩阵。
- `morph-playground` 的 Desktop debug server 仅用于本机开发调试；除非用户明确要求，不限制其认证、授权、来源校验、文件路径或其他权限逻辑。
- 所有 Gradle 模块沿用 `morph-[name]` 命名，基础包名沿用 `li.songe.morph.[name]`。
- `morph-website` 的 Vue 模板禁止使用无障碍专用标签、属性或 API，包括 `aria-*`、`role`、`tabindex` 和仅供屏幕阅读器使用的内容。
- `morph-website` 的 Vue 模板尽量只使用 `div`、`span`、`img` 等没有浏览器默认样式或特定交互语义的标签；除非实现功能确实需要，否则不使用 `button`、`a`、`header`、`main`、`nav`、`section` 等标签。
