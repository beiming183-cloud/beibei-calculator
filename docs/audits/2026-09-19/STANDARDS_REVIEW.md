# Standards 轴审查

基于 ARCHITECTURE.md、ARCHITECTURE_REVIEW.md、Stage 6 接力约定检查 Android 输入和计算任务边界。以下 4 项均为比较基线已有问题，最新增量尚未修复。只记录有具体影响的缺陷，不把大文件或代码气味本身当作错误。

## A1 [P1] OK 在主线程计算

CalculatorView.java:2191 仅对 EXE 启动 worker；同文件:2209 直接 dispatch 其他键。CnCwMachine.java:1065 的 OK/ENTER/EXE 均会 evaluate。

普通计算输入耗时 sum 后按中央 OK 会阻塞 UI，包括 AC。违背 ARCHITECTURE.md:78 的禁止主线程阻塞计算规则。

证据：静态调用链。未实际启动巨大求和。

## A2 [P1] AC 没有终止数值任务

CalculatorView.java:104 使用单线程 executor，:2243 仅 cancel(true)。NumericAnalysis.java:89 的求和循环不检查中断，因此会挡住后续任务。

证据：StateProbe 实跑，线程已中断时仍完整执行 10000 次、返回 50005000；有限队列实验中旧任务被取消后仍执行完循环。Android 后续 EXE 被排队的影响来自已核对的调度结构。违背 ARCHITECTURE.md:132 的 cancellable snapshot 契约。

## A3 [P2] 粘贴/触摸编辑绕过 revision

CalculatorView.java:2163 直接修改 machine；:2199 的旧异步回调检查不到变化，随后以 snapshot 覆盖 machine/state。选区、光标、工作流选格存在相同直接修改路径。

慢 EXE → 粘贴 → 旧结果返回会丢失粘贴。违背 ARCHITECTURE.md:142 的慢计算不得覆盖新输入契约。

证据：静态状态链；待 Android 时序复现。

## A4 [P1] 多指交叉释放漏停重复输入

CalculatorView.java:1735–1763：A 先按显示区，B 再按数字键，A 先松开。POINTER_UP 不清 displayPressed；B 最后松开时误走显示区 ACTION_UP 分支并 return，跳过 B 的 pointerUp/stopKeyRepeat。

:1795 的重复回调仍每 72ms 输入。违背 ARCHITECTURE.md:59 的双指交叉释放规则和长按指针释放即取消的注释契约。

证据：完整静态事件轨迹；待设备多指复现。

详细源码固定链接、修复方向见 [完整报告](REVIEW.md#standards)。本轴 4 项；主要严重风险为主线程阻塞、无法终止旧计算和抬指后输入不停。
