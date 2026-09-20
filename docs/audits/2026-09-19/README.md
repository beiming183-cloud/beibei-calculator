# 2026-09-19 全项目审查归档

> **已有后续修复**：以下结论是修复前的冻结审查记录。用户随后授权实施；最新代码、自动化结果、防持续高负载措施和剩余边界请先读 [FIXES.md](FIXES.md)。原始反例日志保留，不用修复后的输出覆盖历史证据。

审查代码：stage6/integration @ edb97fd1936b4c9ddc01d1a09773a63de5ba1646。
比较基线：此前验收包对应的 aeedc2429dab4979459a69dd3986fe521697f8f2。

**结论：已有测试和构建通过，但主要审查清单仍有 15 项问题，当前不建议合并或发布。** 其中 12 项有独立 JVM 引擎/公开状态机运行证据，3 项是 Android 静态调用或触摸事件路径发现，尚需设备复现。本目录记录问题，没有修改生产代码。

## 阅读顺序

1. [完整报告、全部主要问题与修复顺序](REVIEW.md)
2. [架构/规范审查与证据类型](STANDARDS_REVIEW.md)
3. [新增功能契约专项审查](SPEC_REVIEW.md)
4. [数值正确性专项审查与额外边界](NUMERIC_REVIEW.md)
5. [交接待办及验收范围](HANDOFF.md)

## 实际验证记录

- [核心 14 套回归、Debug 构建和 lint 日志](validation.log)
- [Android lint 详细输出](android-lint.txt)：0 errors、3 warnings。
- [对应被审查提交的 GitHub CI](https://github.com/beiming183-cloud/beibei-calculator/actions/runs/35234132710)：success。
- [复现源码 SpecProbe.java](SpecProbe.java) / [实际输出](SpecProbe-output.txt)
- [复现源码 NumericProbe.java](NumericProbe.java) / [实际输出](NumericProbe-output.txt)
- [复现源码 StateProbe.java](StateProbe.java) / [实际输出](StateProbe-output.txt)

源码定位链接固定到被审查提交，后续修复移动行号不会改变本次证据含义。日志中的本地路径来自实际执行环境，不是其他环境必须采用的目录。

## 复现方法

在包含本归档的仓库根目录使用 Java 17，先运行（重放修复前反例时，须使用上面的被审查提交所编译的 core；当前分支会体现修复后的行为）：

```powershell
.\gradlew.bat :core:classes
$reviewClasses = Join-Path $env:TEMP ('beibei-review-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $reviewClasses | Out-Null
javac -encoding UTF-8 -cp core/build/classes/java/main -d $reviewClasses docs/audits/2026-09-19/SpecProbe.java docs/audits/2026-09-19/NumericProbe.java docs/audits/2026-09-19/StateProbe.java
java '-Dfile.encoding=UTF-8' -cp "$reviewClasses;core/build/classes/java/main" SpecProbe
java '-Dfile.encoding=UTF-8' -cp "$reviewClasses;core/build/classes/java/main" NumericProbe
java '-Dfile.encoding=UTF-8' -cp "$reviewClasses;core/build/classes/java/main" StateProbe
```

这些程序打印期望与实际，用于重放反例；不是已经修好的永久测试，也不会自动声称所有输出通过。StateProbe 的取消实验使用有限循环和同步信号，没有启动数十亿项求和。

Linux/macOS 可使用相同 javac/java 参数，将 classpath 分隔符从分号改为冒号。要重放原始结果，请使用被审查提交的 core 类；后续代码修复后结果应发生变化。

## 范围

本次检查了最新增量、计算引擎、工作流输入/结果、历史/Ans、Android 输入与后台任务、构建及现有测试。未将全面手册一致性、物理参考机差分或全部真机交互标为完成。原有函数表、多项式升降阶、联立增减元及同包覆盖安装的通过记录继续有效；本报告中的求根算法反例和新增功能是不同的验收目标。
