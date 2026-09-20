# 笔画推断稳定性审阅（2026-09-20）

结论：保留 `ExperimentalStrokeInference` 和示例中的 experimental 标记，默认 Auto 不变。
本次补充验证入口与反例，没有修改生产算法，也没有推广视觉基线。

广域报告共 5,563 个序列：3,780 个实际采用推断，1,783 个回退（包含相同输入保留原表示）。
96px 近端点误差计数全部为 0，没有规划或渲染异常。这是首次候选生成，全部标记为新增，
不是与已接受基线一致。人工抽查包括 Add/Check、左右箭头的三种旋转偏好、大尺寸 Check 对照，
以及 Arrow Back Ios/Keyboard Backspace、North West/South East、Remove/Add、Check/Arrow forward，
没有逐条人工审阅全部 5,563 个序列。

首次 `verifyMorph --continue`：68 项 JVM 测试通过；47 项 Desktop 测试中 46 项通过，
新增的大尺寸覆盖检查因未经标定的阈值失败；Android/Wasm 编译和 API 检查通过。
quick 仍为 120 个序列中 60 个旧基线差异，与之前恢复原版图标的结果相同。
完整验证未通过，不能作为稳定版发布依据。

撤销未经标定的栅格硬门槛后，重新执行 `:morph-playground:desktopTest`，47 项全部通过。
误差数据和审阅图继续生成；生产算法、几何重建容差和既有端点断言未改动。
本次仅重跑 Desktop 测试，不将它表述为完整 `verifyMorph` 通过；旧视觉基线差异仍待独立处理。

## 可复现的检查

- `:morph-playground:visualRegression -Pmorph.visual.suite=infer-wide -Pmorph.visual.mode=candidate -Pmorph.visual.animation=none`：
  全目录自身、步长 37 双向抽样，以及探测所得 15 个图标的全部有向组合。
  探测组合覆盖三种轮廓策略、三种旋转偏好、两种插值；其余目录抽样使用默认配置。
- `StrokeInferenceTest.repeatedInterruptionsPreserveStrokeSnapshotsAcrossDirectionChanges`：
  两种插值各连续中断 24 次、切换旋转偏好和目标，校验原始快照点集与有限坐标。
- `InferredStrokeIconTest.largeScaleEndpointReconstructionIsMeasuredAndReviewable`：
  Close/Arrow back、Close/Check、Remove/Close 的两种插值与两端，768px 覆盖误差及审阅图。

## 已发现的问题

1. Close → Check 的目标近端点，在 Polar / Linear 下累计 alpha 绝对误差比分别为
   0.0058501117 / 0.0058823929。这些数值作为诊断保留，不单独判定算法异常。
   此前使用 0.003 栅格硬门槛缺乏标定依据，现已撤销；几何重建的 0.3% 及原有 96px 端点检查不变。
   这些案例没有单像素 alpha 差超过 128 的像素，不能把累计误差描述成肉眼可见的强烈闪烁。
   几何区域差、抗锯齿与中心线重采样后的覆盖差不是同一个指标，后续应分开定位。
2. Add → Check 的中间帧会露出额外的折角；左右箭头互换会产生笔画交叠。
   较低的候选评分并不足以保证更自然的运动。
3. 当前 `inferenceMotionQuality` 只采样 Polar，虽然验证覆盖 Linear，选择评分并未对 Linear 同时优化。
   自交指标只统计单条中心线内部的严格相交，不统计不同笔画之间新增的交叠或共线重合；
   交汇处距离只惩罚分离，也不能替代交叠评估。

报告图位于 `morph-playground/build/reports/morph-visual/infer-wide/candidate/`：

- `6_14_SharedBoundary_Auto_Polar.png`：Add → Check。
- `15_16_SharedBoundary_Auto_Polar.png`：Arrow back → Arrow forward。
- `327_415_SharedBoundary_Auto_Polar.png`：North West → South East，途中出现折叠。
- `7_6_SharedBoundary_Auto_Polar.png`：Remove → Add，途中出现交叠。

768px 对照位于 `morph-playground/build/reports/stroke-inference/large-Close-Check.png`，
三列依次为原版目标、目标近端点、动画中点；数值见同目录 `large-endpoints.tsv`。
以上名称及编号仅用于复现测试，不进入生产算法选择。

## 后续改进顺序

先拆分几何重建、重采样与栅格化的误差来源，收集多尺寸数据后独立标定栅格指标，
确认实际缺陷后再修正；为候选选择增加跨笔画交叠及两种插值的评估，
并为新增约束构造不依赖图标名的几何性质测试。重新生成专属报告后复核这些反例；
不能只降低阈值、推广参考图或删除 experimental 文案来完成稳定化。

本次没有验证全部 504×504 组合、全部连续时刻、浏览器运行或帧率；
广域候选生成通过也只表示对应的异常/端点检查通过，不代表所有中间帧已获人工认可。
