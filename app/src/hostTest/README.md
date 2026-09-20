# Android 输入主机回归

本目录测试直接编译、运行生产 `CalculatorView`、`PhysicalKeyLayout` 和
`CnCwTouchRouter`，使用真实 core；`android/` 中仅替换 JVM 无法运行的
Android 系统 API。替身不复制计算器业务逻辑，不进入 APK。

Handler 使用可手动推进的主线程队列。执行器用 CountDownLatch 阻塞/放行，
故意固定“计算后、回调前、用户编辑”的顺序，不依赖机器速度或大型计算。
MotionEvent 明确指定 pointerId 和 actionIndex，重放交叉抬指序列。

## 运行

先完成 `:core:classes :app:generateCn991DebugBuildConfig`，然后在仓库根目录：

```powershell
./app/src/hostTest/run.ps1 -AndroidSdk 'C:/Users/rog/AppData/Local/Android/Sdk'
```

脚本先用真实 Android SDK 编译生产源码，再编译测试替身，最后把替身放在
运行时 classpath 前部。产物仅写入 `app/build/hostProduction`、`app/build/hostTest`。

## 已锁定的行为

- 普通计算的 OK、ENTER 都走执行器；结构化表单的 OK 下一格仍同步完成。
- 粘贴拒绝尚未执行和已经执行、尚未发布的旧结果。
- 触摸光标、长按选区、工作流选格使旧结果失效。
- 显示区和数字键交叉抬指之后，不再连续输入。
- 正常按住数字键仍连续输入，抬指即停。
- 失焦、脱离窗口取消手势；失焦取消计算，重挂载可再次计算。

修复前原始 6 项全部失败：OK/ENTER 在调用线程执行、旧结果覆盖 `1+2` 为 `1`、
双指离开后输入变成 `1111111111`、失焦和销毁后仍重复输入。修复后 14 项通过。

## 验证边界

这是确定性的主机行为测试，**不是 Android 设备/模拟器验收**。字体测量、绘制、
真实触感、系统事件派发时机、电池消耗及机身温度都不在替身断言范围内。
功耗相关结论仅限于已证明的：失焦/销毁后不再保留重复输入任务，旧计算被取消。
仍需在手机上观察实际温度与触摸表现；不能据此宣称“手机不会发热”。
