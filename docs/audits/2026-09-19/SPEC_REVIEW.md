# Spec 轴审查证据

审查 HEAD：`edb97fd1936b4c9ddc01d1a09773a63de5ba1646`；比较基线：`aeedc2429dab4979459a69dd3986fe521697f8f2`。
源码根目录：`D:/Codex/Projects/fx991-stage6-audit`。
独立复现：本目录 `SpecProbe.java`；真实执行输出：`SpecProbe-output.txt`。未修改项目源码。

## S1 [P1] HEX 数字交给十进制表达式校验，合法输入被阻断

- 需求：`README.md:14` 的“矩阵、向量、进制和单位换算等工具”；新增用户路径回归 `core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java:172` 的“HEX A OR 5 = F through Machine path”，共同声明十六进制用户工作流可用。
- 实现：`core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java:287` 把 Base-N 数值定义为通用 EXPRESSION；`CnCwWorkflowValidation.java:89` 以 `ScalarExpressionEngine.compile` 解析所有格；`CnCwMachine.java:1265` 在 BaseNEngine 前拦截。
- 最小复现：HOME → Base-N → 进制转换 → 输入进制 HEX → 输出进制 DEC → 数值按 `1`、`VAR_E` → EXE。
- 预期：DEC=30。实际：cells `[16, 10, 1E]`，提示“输入格式错误 · 进制转换 · 数值 · 1,3”，不产生结果。
- 根因：`1E` 被当作不完整科学记数法；`1E+1` 反而能通过 scalar 校验，但不是 Base-N 数字。应按格所属进制校验，而不是用 scalar compile。
- 归属：新增 Base-N 表单引入。

## S2 [P1] 存储矩阵/向量结果把非零小量舍入成 0

- 需求：`docs/hand-offs/STAGE6_MANUAL_GAP_VALIDATION.md:22` 明确“MatAns / VctAns 回归改为同时验证正式答案标题与真实 payload”；`:15` 声明“formatter 设置真实生效”。
- 实现：`core/src/main/java/com/codex/fx991/core/cw/CnCwLinearAlgebraMemory.java:277` 固定 `setScale(11)`，`:240` 将该字符串直接写入用户结果 cells；未使用显示设置或有效数字策略。
- 最小复现：HOME → 矩阵 → 定义 MatA（1×1）→ 输入 `0.000000000001` → EXE；再 HOME → 同一矩阵应用 → 转置 → EXE。
- 预期：保存页和转置页显示 1×10^-12 或等值小数。实际：`MatA cells=[0]`、`Trn MatA cells=[0]`，但 `Ans=1.0E-12`，存储值与用户可见 payload 矛盾。
- 同一 formatter 用于存储向量及标量运算输出。此处显示 0 会误导用户且污染复制出的数值；不只是美观问题。
- 归属：新文件引入，旧 `CnCwModeEngine.format` 也有同样策略，宜一起集中修正。

## S3 [P1] 结构化运行时错误返回后把整个内部输入串写入一格

- 需求：`docs/hand-offs/STAGE6_DEVICE_ACCEPTANCE.md:120`：“错误 → OK / 返回回到错误位置”；`docs/hand-offs/STAGE6_RELAY.md:41`：“可定位到具体输入格的错误状态”；`:172`：“Ans、历史、复制粘贴、错误恢复和异步 input revision 门禁不得回归”。
- 实现：`core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java:1279`–`:1286` 在 EXE 时将整张表序列化到 tokens；`:888` 错误 OK 只调用 `dismissError`；`:2250`–`:2257` 不恢复 selectedCell；`:1299` 把 tokens 写回当前格。
- 最小复现：HOME → 统计 → 单变量（频数）→ x=10 → OK → 频数=-1 → EXE → Argument ERROR → OK → 触摸 x 格（`selectWorkflowCell(0,0)`）。
- 预期：回到频数格编辑 -1，切换到 x 格后数据仍为 `[10,-1]`。
- 实际：错误 OK 后 expression=`10,-1`，切到 x 后 cells=`[10, 10,-1]`；整个内部串覆盖频数，继发格式错误。用户尚未编辑已损坏工作表。
- 归属：错误恢复链基线已存在，新增频数/矩阵运行时错误令路径更常见；全项目审查应记录。

## S4 [P2] 连按 HOME 绕过应用切换清理

- 需求：`docs/hand-offs/STAGE6_MANUAL_GAP_VALIDATION.md:14`：“MatAns / VctAns 按对应应用答案记忆语义维护”；永久回归 `CnCwFunctionalitySuite.java:328` / `:346` 明确“launching another app clears MatAns/VctAns”，`:371` 明确“HOME then another app disables verification mode”。
- 实现：`core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java:2271` 每次 HOME 都执行 `applicationBeforeHome = application`；第一次 HOME 已令 application=null，第二次抹掉来源。`:2311` 的非空检查遂跳过清理。
- 最小复现 A：计算 → Tools 开验证 → HOME → HOME → 统计。实际 verificationMode 仍为 true。再 HOME → HOME → 计算 → `2+3` → EXE，得到 No Operator，而应为 5。
- 最小复现 B：矩阵定义 MatA=5 → 矩阵平方得到 MatAns=25 → HOME HOME → 计算 → HOME HOME → 矩阵 → MatAns。实际仍显示25，而该答案按新增生命周期应已清除。
- 归属：新增应用切换清理的边界遗漏。

## 文档/范围说明（不计入上述 4 个运行时缺陷）

`STAGE6_RELAY.md:103` 仍写“Complex / Base-N 暂不混入这一轮结构化表单工作流”，但 HEAD 已新增 Base-N 表单和复数入口；`STAGE6_DEVICE_ACCEPTANCE.md:9` 仍推荐旧 artifact `10495340801`，其构建早于这些能力。应补一份对应 HEAD 的能力/验收差集，不能把先前 APK 验收等同于新增能力已验收。现有资料不足以断言扩展未经用户授权，因此仅记为规格与实现状态不同步。
