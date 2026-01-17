github.md
📝 约定式提交 (Conventional Commits) 规范详解
这种规范要求提交信息具有清晰的结构，格式如下：
<类型>[可选 范围]: <简短描述>

[可选 正文]

[可选 脚注]

1. 提交类型 (Type)
   这是最核心的部分，用于说明本次提交的性质。常用的类型包括：

| 类型 | 说明 | 示例 |
|------|------|------|
| feat | 新增功能 | feat(user): 添加微信登录功能 |
| fix | 修复bug | fix(order): 修正支付超时未关闭的问题 |
| docs | 仅文档更改 | docs: 更新API接口文档 |
| style | 不影响代码含义的更改（空格、格式等） | style: 调整代码缩进 |
| refactor | 既非新增功能也非修复bug的代码重构 | refactor(utils): 简化密码加密逻辑 |
| perf | 性能优化 | perf(image): 使用WebP格式减小体积 |
| test | 增加或修改测试 | test(login): 添加多设备登录测试用例 |
| build | 影响构建系统或外部依赖的更改 | build: 将Webpack升级至v5 |
| ci | 更改持续集成配置或脚本 | ci: 为GitHub Actions添加自动化部署 |
| chore | 其他不修改源码或测试的杂项变更 | chore: 更新项目README.md |
| revert | 撤销之前的提交 | revert: feat(cart): 回滚购物车功能 |

2. 范围 (Scope，可选)
   用于说明影响的范围，通常是模块、组件或文件名。例如 feat(auth):、fix(homepage):。

3. 简短描述 (Subject)
    - 时态：使用祈使句、现在时。例如用 "add"，而不是 "added" 或 "adds"。
    - 首字母：不要大写。
    - 标点：结尾不要加句号。
    - 长度：建议不超过50个字符。

4. 正文 (Body，可选)
   在简短描述后空一行，详细说明本次提交的动机、与之前行为的对比。同样使用现在时。
    - 换行长度：建议每行不超过72个字符
    - 内容：解释"什么"和"为什么"，而不是"如何"实现

5. 脚注 (Footer，可选)
   不兼容的变更 (BREAKING CHANGE)：如果本次提交包含与旧版本不兼容的变更，必须以BREAKING CHANGE:开头，后接描述。这通常会导致主版本号升级。

   关联Issue：用于关闭Issue，例如 Closes #123, Fixes #456。

💡 最佳实践与工具推荐
- 分开提交：一次提交只做一件事。修复了两个bug？请分两次提交。
- 善用范围：在项目复杂或团队较大时，使用范围（如feat(auth):）能快速定位变更。
- 中文还是英文：优先使用英文，便于全球化协作。但如果团队内部约定，使用中文也是完全可以的。
- 提交长度：建议提交信息总长度不超过72个字符，以便在Git工具中完整显示。

常见错误示例：
❌ 错误示例：
- Fix bug #123 (缺少类型前缀)
- feat: Add user login function and update styles and fix some issues (一次提交做了多件事)
- Feat(User Module): Add login feature (首字母不应大写，且有句号)

✅ 正确示例：
- feat(user): add login function
- style(user): update login form styles
- fix(user): resolve login timeout issue

自动化工具：可以利用以下工具确保规范执行：
- Commitizen: 命令行交互式工具，引导你生成规范提交信息。
- commitlint: 用于检查提交信息是否符合规范，可集成到Git钩子中。
- Standard Version: 基于规范的提交信息，自动生成更新日志(CHANGELOG)和升级版本号


示例:
fix(payment): 修复微信支付回调金额单位错误

- 回调处理中将"分"错误地识别为"元"，导致金额错误
- 修正 `PaymentCallbackHandler` 中的单位转换逻辑
- 紧急修复，影响线上交易

Fixes #CVE-2024-001
BREAKING CHANGE: 此修复要求商户后台同步更新回调验证逻辑。

refactor(utils): 将日期工具函数重构为独立模块

- 从 `utils.js` 中抽离所有日期相关函数至 `dateUtils.js`
- 统一日期格式为ISO标准 (`YYYY-MM-DD`)
- 函数行为无任何变化，为后续国际化做准备
