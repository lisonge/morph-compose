# 项目协作规则

- 修改变形算法前先阅读 `docs/quality.md`，明确修改的是输入规范化、轮廓对应、策略选择还是插值渲染；禁止按具体图标名称或注册表编号选择算法。
- 算法修改应补充不依赖图标名称的性质测试，并执行 `./gradlew.bat verifyMorph`。扩大策略适用范围时运行 wide 视觉检查，或明确记录尚未验证的范围，不能把 quick 通过表述为全部图标组合正确。
- 视觉检查失败时不得自动覆盖参考图；基线更新必须独立说明接受的差异与原因。实验策略改为默认应作为单独的行为变更说明，不能夹带在单个图标修复中。

- `morph-playground` 的 Kotlin/Wasm 目标仅用于浏览器演示。除非用户明确要求，不执行浏览器端运行测试、截图测试或自动化测试。
- 涉及算法、Compose UI 或共享状态的改动，优先通过 JVM 单元测试和 Desktop/JVM 应用实跑验证。
- 修改共享代码时应保持 Wasm 目标可编译；不要求为 Wasm 建立与 JVM 对等的测试矩阵。
- `morph-playground` 的 Desktop debug server 仅用于本机开发调试；除非用户明确要求，不限制其认证、授权、来源校验、文件路径或其他权限逻辑。
- 所有 Gradle 模块沿用 `morph-[name]` 命名，基础包名沿用 `li.songe.morph.[name]`。
- `morph-website` 的 Vue 模板禁止使用无障碍专用标签、属性或 API，包括 `aria-*`、`role`、`tabindex` 和仅供屏幕阅读器使用的内容。
- `morph-website` 的 Vue 模板尽量只使用 `div`、`span`、`img` 等没有浏览器默认样式或特定交互语义的标签；除非实现功能确实需要，否则不使用 `button`、`a`、`header`、`main`、`nav`、`section` 等标签。
