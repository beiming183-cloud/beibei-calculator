# 北北计算器远端最新代码审查

> 本文固定记录修复前的问题。随后实施的修复和验证见 [FIXES.md](FIXES.md)；本文的“未修复/不建议合并”描述不能替代最新状态，但本轮依然没有完成真机功耗验收或授权合并。

审查日期：2026-09-19。

结论：当前不建议合并或发布。构建与既有回归全部通过，但有可复现的计算错误、工作表数据损坏及结果状态串用；Android 计算调度和多指释放还存在明确静态风险。

## 审查对象与验证边界

- 仓库：beiming183-cloud/beibei-calculator。
- 已 fetch 远端，最新开发分支 stage6/integration，HEAD 为 edb97fd1936b4c9ddc01d1a09773a63de5ba1646；这也是 PR #9 当前 head。main 仍为 9a549c091df673742ef1cb5081fa738946bb5ade。
- 审查副本：D:/Codex/Projects/fx991-stage6-audit，已切换到该 HEAD。
- 增量比较基线：此前函数表验收包提交 aeedc2429dab4979459a69dd3986fe521697f8f2；同时检查当前全量核心链路，不将既存缺陷冒充新回归。
- 原工作目录 D:/Codex/Projects/fx991-smooth 保留不动，CnCwMachine.java 的既有未提交修改仍在。
- 审查阶段未改计算器源码、未运行仓库中的待应用修补脚本，也未推送、合并或发布；复现程序单独存放在 D:/Codex/Projects/fx991-stage6-audit-review。随后按用户要求，将本报告、复现源码、日志和交接清单归档到 stage6/integration；归档提交仅含审查材料，不包含生产修复，也不合并 PR 或发布 APK。
- 需求依据：README、ARCHITECTURE、ARCHITECTURE_REVIEW、Stage 6 接力/验收/手册差集文档，以及已存在的永久用户入口测试。缺少 issue-tracker 配置，不代表没有项目内需求来源。

实际执行：

| 检查 | 结果 |
| --- | --- |
| :core:check | 14 个 suite，共 1,310 项检查通过 |
| :app:assembleCn991Debug | 通过 |
| :app:lintCn991Debug | 0 errors、3 warnings |
| 最新 HEAD 的 GitHub CI | [35234132710](https://github.com/beiming183-cloud/beibei-calculator/actions/runs/35234132710) success |
| 三组独立 Java 反例 | 针对最新编译产物执行；见末尾源码及日志 |
| PR #9 | open、Draft；本轮只读核对 |

lint 的三项为 OldTargetApi、LockedOrientationActivity、UnusedResources；没有把竖屏设计或普通 lint 建议当作本次阻断缺陷。此次未运行 Release 签名发布，也未将 JVM 验证描述为真机验证；没有重复安装 APK 或重跑此前三项模拟器验收。

下面按 Standards 和 Spec 分开报告。P1 表示应优先修复的算错、输入损坏、失去响应或主要功能不可用；P2 表示特定边界下的功能/状态错误。主要清单 15 项：12 项有 JVM 引擎或公开状态机运行证据，3 项为 Android 静态路径证据。后者仍需设备时序/多指复验。

## Standards

### A1 [P1，既存，静态] 中央 OK 绕过 worker，在主线程执行计算

位置：[CalculatorView.java:2191](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L2191)、[CnCwMachine.java:1065](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L1065)。

View 只把 EXE 送入工作线程，但核心把 OK、ENTER、EXE 都视为执行键。普通计算中输入大范围 sum 后按中央 OK，会沿 UI 线程同步进入求和循环，绘制、触摸和 AC 都被阻塞。没有为了验证而实际启动数十亿次循环。

违反 ARCHITECTURE.md 的 “must not … [introduce] blocking calculation work on the main thread” 要求。修复应统一识别会产生计算任务的语义动作，同时保持结构化表格中 OK 只切换输入格的规则。

### A2 [P1，既存，运行＋调用链] AC 只取消 Future，不能停止旧数值任务

位置：[CalculatorView.java:104](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L104)、[CalculatorView.java:2243](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L2243)、[NumericAnalysis.java:89](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/NumericAnalysis.java#L89)。

界面使用单线程 executor；AC 调用 cancel(true)，而 sum 循环完全不检查中断。独立程序预先设置线程中断，再求和 1…10000，实际仍调用被积函数 10000 次并返回 50005000。有限的队列实验也确认，标记取消的旧任务仍执行完循环才放行下一任务。

因此大求和后按 AC，再输入 1+1，新 EXE 会排在旧任务后。ARCHITECTURE.md 的 “isolated, cancellable snapshot boundary” 尚未成立。应加入协作式取消、合理计算预算和超时处理，并测试取消后下一次计算的完成时间。

### A3 [P2，既存，静态] 慢计算结果会覆盖已接受的粘贴和触摸编辑

位置：[CalculatorView.java:2156](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L2156)、[CalculatorView.java:2199](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L2199)。

pasteClipboardText 直接修改 machine，却不递增 inputRevision 或取消正在计算的任务。触摸选区、移动光标和选择工作流格也有相同旁路。旧回调仍通过 revision 检查，随后整体替换 machine/state。

触发序列为慢 EXE → 长按粘贴 → 旧任务返回；已接受的新内容会丢失。静态路径已确认，未在本轮进行 Android 时序复现。违反 “a slow calculation cannot overwrite newer input”。应把全部可变输入统一经过 revision/任务失效入口。

### A4 [P1，既存，静态] 两指交叉释放后，数字键可能持续自动输入

位置：[CalculatorView.java:1735](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L1735)、[CalculatorView.java:1789](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/app/src/main/java/com/codex/fx991smooth/CalculatorView.java#L1789)。

A 先按显示区，B 再按数字键；A 先松开时发生 POINTER_UP，displayPressed 未清理。B 最后松开时进入显示区 ACTION_UP 分支并提前 return，跳过 B 的 pointerUp 和 stopKeyRepeat。重复回调因而仍可每 72ms 派发一次数字输入。

这违反架构的双指交叉释放约定以及长按回调“指针释放即取消”约定。完整事件路径已核对，待多指设备复现。应记录显示区所属 pointerId，并确保每个指针都执行自己的释放清理。

Standards 轴共 4 项，主要严重风险是主线程阻塞、取消失效和抬指后输入不停；基线气味仅作为架构建议，不另外充当缺陷。

## Spec

### B1 [P1，新增，运行] 合法十六进制数被表单校验拒绝

位置：[CnCwWorkflowSpec.java:287](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java#L287)、[CnCwWorkflowValidation.java:89](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowValidation.java#L89)。

HOME → Base-N → 进制转换 → HEX 转 DEC → 输入 1E → EXE，预期 30，实际“输入格式错误”，cells 为 [16, 10, 1E]。新表单将 Base-N 数值当作普通标量表达式校验，1E 被当作不完整的科学记数法。

README 声明进制工具可用，新增测试也声明 HEX 用户路径成立。应让字段类型/校验器识别当前进制，不能对全部字段统一调用 scalar compile。

### B2 [P1，新增，运行] 存储矩阵/向量把非零小数显示成 0

位置：[CnCwLinearAlgebraMemory.java:277](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwLinearAlgebraMemory.java#L277)。

定义 1×1 的 MatA=[0.000000000001]，保存并转置：两次用户结果 cells 都为 [0]，但实际 Ans 为 1e-12。固定保留 11 位小数，把非零有效数直接舍掉；向量共用相同 formatter。

手册差集文档要求验证“真实 payload”并声明格式设置生效，这里显示与内部数值不一致。应复用统一有效数字/科学记数 formatter，保留结构化数值；旧 ModeEngine 中相同策略也要检查。

### B3 [P1，既存链路被新增频数功能触发，运行] 错误恢复会把整张表串写进一个格

位置：[CnCwMachine.java:888](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L888)、[CnCwMachine.java:2250](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L2250)、[CnCwMachine.java:1299](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L1299)。

统计频数输入 x=10、频数=-1 → EXE → Argument ERROR → OK → 点 x 格。预期仍是 [10,-1]，实际频数被改成整个内部输入串，cells 变为 [10, 10,-1]。

EXE 将整表序列化到 tokens，dismissError 只关错误状态，没有重新加载选中格；下一次 commitWorkflowCell 把整表 tokens 写进了单格。直接违反“错误 → OK / 返回回到错误位置”“错误恢复不得回归”。错误位置应携带工作流格坐标，恢复时加载该格原始输入，并区分求值源和编辑源。

### B4 [P2，新增边界遗漏，运行] 两次 HOME 绕过应用切换清理

位置：[CnCwMachine.java:2271](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L2271)、[CnCwMachine.java:2311](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L2311)。

计算应用开验证 → HOME HOME → 统计 → HOME HOME → 计算 → 2+3，实际 No Operator，预期 5。类似操作也让 MatAns 在跨应用后继续保留，违反新增永久测试定义的生命周期。

第一次 HOME 已把 application 置空，第二次又把来源 applicationBeforeHome 覆写为空，清理条件便失效。HOME 应幂等，并在真正离开应用时保存来源。

### B5 [P1，既存，运行] 无实根不等式错误地返回“无解”

位置：[PolynomialEngine.java:108](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/PolynomialEngine.java#L108)。

x²+1>0 应为全体实数，实际无解；−x²−1<0 也一样。已同时验证底层 API 和公开 ModeEngine 输出。没有实根时取样区间为 (−∞,+∞)，现有采样计算产生 NaN，所有关系判断均为 false。

Stage 6 声明四种不等式关系可求解，这属于基础解集错误。应为整个实轴选择有限采样点，并对无实根正/负多项式覆盖四种关系。

### B6 [P1，既存，运行] 三/四次重根被返回为伪复根

位置：[PolynomialEngine.java:43](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/PolynomialEngine.java#L43)。

(x−1)^4=0 预期四个根均为 1，实际返回如 0.9983701907−0.0023503041i 的伪复根。(x−1)^4≤0 应只有 x=1，实际无解；(x−1)^3>0 应为 x>1，实际也无解。

迭代满 300 次后没有收敛/残差检查便返回，后续不等式再按虚部阈值筛掉了真实重根。应处理重根并验证求根可靠性，不能仅放宽虚部阈值来遮盖错误。

### B7 [P1，既存，运行] 复数求值把 log 当成 ln

位置：[ComplexExpressionEngine.java:132](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/ComplexExpressionEngine.java#L132)。

复数模式 log(100) 应为 2，实际为 4.605170186；普通模式输入 sqrt(-1)+log(100) 也会自动走复数引擎，实际 4.605170186+i，预期 2+i。

两种底数在 switch 中合并到了自然对数。修复常用对数的底数，并增加普通/复数两条求值路径的一致性测试。

### B8 [P1，既存，运行] NaN 和无穷值被作为成功结果存进 Ans

位置：[ComplexExpressionEngine.java:16](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/ComplexExpressionEngine.java#L16)、[ComplexExpressionEngine.java:87](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/ComplexExpressionEngine.java#L87)、[CnCwMachine.java:1824](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L1824)。

复数模式 0^0.5 应为 0，实际 NaN+NaNi；ln(0) 应为数学错误，实际 -INFINITY。公开状态机均将其标为 RESULT，Ans 分别为 NaN 和 -Infinity，会污染连续计算。

应处理零底数非整数幂的定义域，统一检查复数实部/虚部的有限性与范围，失败不得走成功提交门。

### B9 [P2，既存，运行] 统计的大偏移数据变成零方差或奇异回归

位置：[StatisticsEngine.java:37](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/StatisticsEngine.java#L37)、[StatisticsEngine.java:129](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/math/StatisticsEngine.java#L129)。

数据 [100000000,100000001] 的总体方差应为 0.25、标准差 0.5，实际为 0。以这些值为 x、[0,1] 为 y 做线性回归，实际报奇异，但唯一拟合应为 y=x−100000000。

平方和减均值平方导致有效数字消减。应采用稳定的中心化或在线统计算法，并对频数版本同步验证。

### B10 [P1，既存，运行] 格式切换误用最新 Ans，改变历史结果数值

位置：[CnCwMachine.java:2211](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L2211)、[CnCwMachine.java:2611](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L2611)。

依次计算 1/2 和 1/3，UP 两次调回 1/2，再 FORMAT → 假分数。实际表达式仍是 1/2，结果却变为 1/3。

历史恢复了结果字符串/typed payload，却没有把格式操作绑定到被查看的结果；activateFormat 继续使用最新全局 ans/exactAns。Stage 6 明确要求历史、格式和结果协议保持正确。格式操作应读取当前被查看结果的数值 payload，同时保留“Ans 是最近计算答案”的独立语义，不能简单把 Ans 改成历史值。

### B11 [P2，既存功能缺口且最新修补未落地，运行] 复数“运算验证”仍不可用

位置：[CnCwMachine.java:1755](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L1755)、[CnCwMachine.java:3000](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java#L3000)、[待应用修补脚本](https://github.com/beiming183-cloud/beibei-calculator/blob/edb97fd1936b4c9ddc01d1a09773a63de5ba1646/scripts/stage6_complex_verify_patch.py)。

复数应用 → 工具 → 开启运算验证 → i^2=-1 → EXE，实际 Argument ERROR，预期 True。开着验证模式输入没有关系符的 i^2，又作为普通计算返回 -1。所有十个应用都展示 verify 入口，但求值仅在 CALCULATE 分支处理它。

HEAD edb97fd 只新增了修补脚本，生产引擎、状态机和测试并没有应用脚本中的改动；正常 CI 不运行该脚本。因此不能把“脚本已提交”记为功能已经修复。需要将修补转换为正式可审阅源码变更、验证实际支持范围，并关闭未实现的入口。

Spec 轴主清单共 11 项，严重问题集中在错误解集、错误数值、无效结果提交及输入格损坏。B1–B4 的需求逐句对应见下面的专项报告。

## 架构判断与文档差距

目前不需要整体重写。核心平台隔离、WorkflowSpec/Session、typed 结果、独立数值引擎与现有回归都有保留价值，但三个边界还没有完成：

1. 所有编辑动作的统一入口：按键有 inputRevision，粘贴/选区/触摸编辑还有旁路。
2. 编辑源、求值源、当前结果、历史结果、Ans 的所有权：仍由 tokens/result/ans/lastExactResult 等字段交叉推导。CnCwCalculationState 目前明确是兼容投影，不是唯一写入状态；B3、B10 正好暴露这一问题。
3. 各数值类型的输入与结果规则：Base-N 不能用标量语法校验，复数不能缺少有限性校验，矩阵/向量不能单独固定小数位。

CnCwMachine 和 CalculatorView 同时承担多类职责属于可维护性风险，但本报告没有将“大文件”本身算作运行缺陷，也不建议为了拆文件改动全部已验收行为。

文档有明显时间差：

- README、FEATURE_COVERAGE、ARCHITECTURE 的阶段/版本/包名有旧描述，不能都当作当前事实。
- STAGE6_RELAY 仍写 Base-N 不进入本轮结构化表单，但当前已进入。
- 验收文档推荐的 artifact 10495340801 早于新增矩阵记忆、频数、进制等提交。旧函数表和覆盖安装的已通过记录继续有效，新增功能必须按对应提交单独验收。
- 没有据此认定后续扩展未经用户授权；这是能力与验收记录同步问题。

## 修复顺序建议

1. 先修会算错的数值链：无实根与重根、log 底数、零的幂/非有限值、历史格式串值和非零显示成零。把本次反例加入永久回归。
2. 完成状态与任务边界：错误恢复按格加载、全编辑路径使旧任务失效、语义执行键统一调度、协作式取消、多指按 pointerId 收尾。
3. 补入口与生命周期：按进制校验、重复 HOME 幂等、正式落地复数验证；稳定统计的数值算法同时补齐。
4. 重跑全套 check、Debug/lint 和对应签名构建；设备仅针对变更影响的交互链复测。保留此前多项式升降阶、联立增减元与函数表分页的已通过结果，数值求根反例是不同的测试目标。

修复完成前保持 PR #9 Draft。合并仍须用户明确授权。

## 可复现证据与后续边界清单

- [Spec 专项报告：需求原句、4 个公开入口反例](./SPEC_REVIEW.md)
- [数值专项报告：5 类主要错误与附录边界](./NUMERIC_REVIEW.md)
- [SpecProbe.java](./SpecProbe.java) / [运行日志](./SpecProbe-output.txt)
- [NumericProbe.java](./NumericProbe.java) / [运行日志](./NumericProbe-output.txt)
- [StateProbe.java](./StateProbe.java) / [运行日志](./StateProbe-output.txt)
- [Android lint 原始报告](./android-lint.txt)

复现程序使用 Java 17 编译，classpath 为 D:/Codex/Projects/fx991-stage6-audit/core/build/classes/java/main；无第三方测试库。

数值附录还记录了：极小系数/根被绝对阈值抹除、标量/复数隐式乘法优先级不一致、复平方根小分量消减、极小虚部显示为零、负大数 asinh 错误、精确三角零点参与除法的残差问题。它们有日志证据，作为下一轮数值边界修复清单，不与上面的主要问题重复计数。

待设备进一步确认：多指交叉释放、慢计算期间粘贴、按 OK 进行耗时计算的响应与取消时延、Base-N 长二进制结果省略后的可读性。本轮没有将未运行的设备测试标成通过或失败。
