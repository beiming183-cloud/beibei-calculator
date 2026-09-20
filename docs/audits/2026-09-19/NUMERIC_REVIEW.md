# 数值审查补充（2026-09-19）

审查 HEAD：`edb97fd1936b4c9ddc01d1a09773a63de5ba1646`。对比 baseline：`aeedc242`。

以下问题全部是既存数值缺陷（`git diff aeedc242 HEAD -- core/src/main/java/com/codex/fx991/core/math` 为空），不是这次最新提交引入。审查只增加独立 probe，未修改项目源码。

验证源：`NumericProbe.java`；实际日志：`NumericProbe-output.txt`。使用当前项目 `core/build/classes/java/main` 运行。已检查 `RegressionSuite.java` 对应数值测试；现有测试集中在小型常规数据，未覆盖下列反例。

## 建议列入正式报告的发现

### [P1] 无实根不等式一律得到“无解”

- 位置：`core/src/main/java/com/codex/fx991/core/math/PolynomialEngine.java:104–111`，最小引用 `108–111`。
- 输入：不等式 `x²+1>0`（关系 1，系数 `1,0,1`）。
- 预期：全体实数。实际：`InequalitySolution[intervals=[]]`；公开 `CnCwModeEngine.evaluate(INEQUALITY,"polynomial","1,1,0,1",context).display()` 也是“无解”。
- 同类 `−x²−1<0` 同样出错。
- 原因：没有实根时唯一分段是 `(-∞,+∞)`，第 108 行计算 `+∞−∞` 得 NaN，之后所有关系都为 false。应选有限测试点（如 0），并覆盖无实根正/负多项式和四种关系。

### [P1] 三/四次重根未收敛却返回错误复数，并让不等式丢失实根

- 位置：`PolynomialEngine.java:31–49`，引用 `43–49`。
- 输入：四次方程系数 `1,-4,6,-4,1`，即 `(x−1)^4=0`。
- 预期：四个根均为 1。实际：`0.998370190737722−0.0023503041386817827i` 等四个明显伪复根；公开模式结果同样展示伪复根。
- 输入 `(x−1)^4≤0` 预期 `{1}`，实际无解；`(x−1)^3>0` 预期 `(1,+∞)`，实际也无解。
- 原因：Durand–Kerner 对重根不稳定，达到 300 次后无收敛检查直接返回；`solveInequality` 以虚部 `1e−8` 筛掉这些真实重根。需要重根识别/可靠求根策略以及残差与收敛检查，不能仅放宽虚部阈值或隐藏显示。

### [P1] 复数路径把 log 当 ln，普通计算遇到虚数也连带算错

- 位置：`core/src/main/java/com/codex/fx991/core/math/ComplexExpressionEngine.java:132`。
- 输入：复数模式 `log(100)`。
- 预期：2。实际：`4.605170186`，状态 `RESULT`；普通模式同式为 2。
- 更直接的普通模式输入：`sqrt(-1)+log(100)` 预期 `2+i`，实际 `4.605170186+i`。
- 原因：`case "ln", "log" -> value.log()` 合并两种不同底数，而 `CnCwMachine` 遇虚数整式切到复数引擎。应将常用对数缩放为 `ln(z)/ln(10)`，保留 `ln` 行为并测试跨路径一致性。

### [P1] 复数引擎会将 NaN/Infinity 作为成功结果写入 Ans

- 位置：`ComplexExpressionEngine.java:16–19`（返回边界）、`87–89`（零底数非整数幂）；`ComplexValue.java:67–68`。
- 输入：复数模式 `0^0.5` 预期 0，实际 UI `NaN+NaNi`，状态 `RESULT`，scalar `NaN`。
- 输入：复数模式 `ln(0)` 预期数学错误，实际 UI `-INFINITY`，状态 `RESULT`，scalar `-Infinity`。
- `CnCwMachine.java:1824–1832,1928–1937` 将无效值存入 `complexAns/ans`。后续 Ans 运算继续被污染。
- 应补零底数幂定义域和统一复数有限值/范围验证，错误不能经过成功提交门。

### [P2] 统计使用平方和相减，正常的大偏移数据被算成零方差或奇异回归

- 位置：`core/src/main/java/com/codex/fx991/core/math/StatisticsEngine.java:36–38`、`129–132`；双变量 `80–83` 同类。
- 输入：一元统计 `[100000000,100000001]`。
- 预期：总体方差 0.25、总体标准差 0.5、样本方差 0.5。实际全是 0。
- 输入：线性回归 x 为上述两个值，y 为 `[0,1]`。预期唯一拟合 `y=x−100000000`；实际抛 `Regression is singular`。
- 原因：原始二阶矩与均值平方消减丢失全部有效差异。应以居中数据或稳定在线算法计算方差与协方差；同样检查带频数路径。

## 已复现的其他缺陷（可作为后续数值修复清单）

1. [P2] `PolynomialEngine.java:176,180–183` 使用绝对阈值丢弃合法非零系数/根。`roots(1e−16,0,−1e−16)` 应为 ±1，实际报 Degree；`roots(1,0,−1e−26)` 应为 ±1e−13，实际 `[0,0]`。应保持缩放不变性，显示近似不能改变内部根。
2. [P2] `ComplexExpressionEngine.java:46–56` 与标量隐式乘法优先级不同：`6/2(1+2)` 普通模式为 1（现有 RegressionSuite 明确要求），复数模式为 9。这是项目现有算式语义不一致，不是对数学歧义自行选边。
3. [P2] `ComplexValue.java:92–95` 复平方根通过 `|z|−Re(z)` 求小分量会消减。`sqrt(1+1e−10*i)` 实际返回纯实数 1，虚部应约 5e−11；用较大分量求另一个分量可避免此问题。
4. [P2] `CnCwMachine.java:4970–4971` 显示将绝对值低于 `1e−14` 的虚部直接吞掉；复数模式 `0.000000000000001i` 实际显示 0，虽引擎保留该虚部。应避免将整个非零结果显示为零。该显示函数在 baseline 中已存在。
5. [P2] `ScalarExpressionEngine.java:974` 的反双曲正弦对负大数消减。`asinh(−100000000)` 预期约 `−19.11382792451231`，实际报 Math/Calculation range；需使用保符号、数值稳定公式。
6. [P2] `ScalarExpressionEngine.java:938–940` 的 DEG `sin(180)` 保留浮点残差，因此 `1/sin(180)` 显示 `8.165619676597685E15`，而输入 `1/0` 报错。需检查精确三角结果是否应在数值计算链中生效，不能只影响格式。

## 检查边界

- BaseNEngine 与 MatrixValue 已读，现有可达范围内未确认额外高把握缺陷；这不是完整数值正确性证明。
- NumericAnalysis.sum 允许近 200 亿项，循环未检查中断。是否在 AC 后占用同一个 worker 阻塞后续计算，交由主审查结合 Android 调度验证。
- 不推荐把 `diff(sin(x),1e8)` 等数值积分/导数难点直接认定产品 bug：需要再结合精度承诺、输入角度单位和独立误差分析。
