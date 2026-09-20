# Stage 6 旧版 991CN CW 手册功能差集验收

更新日期：2026-09-17

分支：`stage6/integration`

PR：Draft PR #9

## 本轮已补

- 复数：`Conjg`、`Arg`、`Re`、`Im` 用户入口与回归；
- 矩阵：`Identity(n)`、平方、立方、元素绝对值、`MatAns`；
- 向量：`VctAns`；
- 线性代数记忆：MatA–MatD / VctA–VctD 继续跨应用保留，关机和“设置与数据”复位会清除；MatAns / VctAns 按对应应用答案记忆语义维护；
- 完整性门禁继续覆盖复数直角/极坐标、47 项科学常数、40 个单位换算以及 formatter 设置真实生效；
- 统计频数入口与七类带频数回归已进入永久回归。

## 最近验证

- run `35231271939`：完整 core 回归、Debug APK 与 artifact 全部 success；
- commit `1c7badaef9d15f5fc780a4fa8db67705dc293b82`：补齐旧版 991 手册确认的剩余复数/矩阵/向量功能；
- commit `44b6ba75f3724d4af48fe7331bfe8da42257c62a`：MatAns / VctAns 回归改为同时验证正式答案标题与真实 payload；
- bot 派生提交对应 run `35232277838` 被 GitHub 标为 `action_required`，没有实际 job；本文件提交用于以正常用户提交重新触发同一 head 的正式 PR CI。

## 型号边界

本轮只补旧版 `fx-991CN CW` 手册确认功能。后续型号/CW II 才出现的功能（例如 `Ref` / `Rref`）不混入本型号。

PR #9 在集中验收完成且用户明确授权前继续保持 Draft，不合并到 `main`。
