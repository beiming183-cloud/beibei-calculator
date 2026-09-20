# 第三轮：统一标量与复数隐式乘法规则

基线：`dfa29ee`，分支 `stage6/integration`。用户明确要求温升以后再测，先处理其他问题。本轮没有测温、耗电采样或压力加热测试。

## 问题与项目规则

原审查已发现普通/复数模式语义不一致，上一轮尚未修复。项目 `RegressionSuite` 明确要求 `6/2(1+2)=1`，本轮沿用现有标量规则，不对有歧义的排版自行更换全项目约定。

| 输入 | 解释 | 两种模式修复后 |
| --- | --- | --- |
| `6/2(1+2)` | `6/(2*(1+2))` | 1 |
| `6/2*(1+2)` | `(6/2)*(1+2)` | 9 |
| `(6/2)(1+2)` | 显式括号覆盖默认结合 | 9 |
| `1/2i` | `1/(2*i)` | 复数模式 -0.5i |

在容易混淆的算式中仍建议使用括号明确分母。幂、一元符号及显式乘除结合方向保持不变；普通模式不因此获得复数功能。

## 诊断与修复

使用 diagnosing-bugs 流程，先把旧的“复数结果为 9”观察性断言改为契约检查，补充两引擎差分，再运行：

```powershell
.\gradlew.bat :core:cwNumericalSafetyTest --no-daemon --console=plain
.\gradlew.bat :core:cwAuditStateTest --no-daemon --console=plain
```

[引擎修复前](before.log)出现 14 组失败，最小对照之一是 `8/2(2)`：标量为 2，复数为 8。[公开入口修复前](state-before.log)确认真实机器的粘贴入口也出错。直接调用引擎就失败，排除了粘贴改写和格式器解释差异；显式乘号及明确分母括号用例不失败，将问题定位到省略乘号的优先级。

根因：复数 `parseTerm()` 将显式乘除和隐式乘法放在同一个左结合循环。修复为与标量相同的分层：显式乘除消费完整 `parseImplicitProduct()`，后者组合相邻基本表达式。不更改 UI、剪贴板格式、Ans 存储、数学显示或通用乘除实现。

这是一处解析职责分层修复，不是重写两个引擎。两个解析器仍独立，后续新增共同语法必须加入跨引擎契约测试，不能只验证一个模式。

## 回归范围

- 17 个固定共同表达式：显式/隐式乘法、嵌套括号、负号、常量、函数、幂、连续除法、Unicode 运算符。
- 5 个复数分母用例：`1/2i`、`i/2i`、`1/2i^2`、`8/2(1+i)`、`8/2*(1+i)`。
- 192 个有界生成组合：正负分子/因子和显式/隐式连接；同时与独立预期公式比较，不仅让两个引擎互相比较。
- 变量、Ans、括号内极坐标因子、隐式零分母拒绝。
- 机器层：普通/复数两模式的粘贴、实际按键序列、显式乘号和关系验证，共新增 8 项。

最终 17 套核心回归 **1,522 项通过**（数值安全 97、状态 88；生成的 192 个表达式属于一个测试组，未额外虚增总项数）；生产 View 主机检查 **16 项通过**。Debug 构建及 lint 成功，仍是 0 errors、3 个原有 warnings。

[完整构建/核心日志](validation.log)、[View 检查](host-after.log)、[第一轮定向修复结果](targeted-after.log)。

```powershell
.\gradlew.bat :core:check :app:assembleCn991Debug :app:lintCn991Debug --no-daemon --console=plain
.\app\src\hostTest\run.ps1 -AndroidSdk $env:ANDROID_HOME
```

## MuMu 检查

既有实例 16，Android 12，1260×2800，ADB `127.0.0.1:16896`。仅覆盖独立调试包 `com.beibei.calculator.debug`；正式包 `com.beibei.calculator` 未卸载/覆盖/清数据，版本仍为 0.3.18 / 332，lastUpdateTime 仍为 `2026-09-17 20:03:49`。

测试包 SHA-256：`B691211AEB3D06209BBC7A68BC5670738CC20240624CCFA000729B987B55776B`。

截图逐张检查：

- [普通模式隐式乘法结果 1](screenshots/01-scalar-implicit.png)。
- [复数模式同式结果 1](screenshots/02-complex-implicit.png)。
- [复数模式显式乘号结果 9](screenshots/03-complex-explicit.png)。
- [SHIFT+9 输入 i，触摸 EXE 后结果 -0.5i](screenshots/04-imaginary-denominator.png)。

未重复多项式/联立方程维度切换验收，未声称真人触感和全功能验收完成。

## 下一步交接

1. 优先继续二次回归稳定性、极端多项式缩放/近重根的差分验证；先保留失败反例再修改。
2. 随后整理非函数表结构化结果页的编辑/结果显示边界，以及主屏卡片直接触摸入口。
3. 功耗/温升按用户要求延后，不把延后写成已通过；既有取消和资源预算保留。
4. PR #9 保持 Draft，云端 CI 通过也不自动合并；合并仍须明确授权。
