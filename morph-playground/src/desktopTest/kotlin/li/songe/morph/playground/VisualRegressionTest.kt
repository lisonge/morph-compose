@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package li.songe.morph.playground

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import li.songe.morph.compose.*
import org.jetbrains.skia.EncodedImageFormat
import li.songe.morph.playground.report.RemoteVisualBaseline
import li.songe.morph.playground.report.ApngEncoder

/** Opt-in Desktop suite. Never updates committed baselines, even when comparisons fail. */
class VisualRegressionTest {
    private val pixels = 96
    private val progress = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private data class Result(val id: String, val title: String, val status: String, val changed: Int,
        val endpoint: Int, val areaJump: Int, val sharedDrift: Int, val holeChanges: Int, val trace: String, val animated: Boolean = false)

    @Test fun compareDeterministicCorpus() {
        val root = File(checkNotNull(System.getProperty("morph.visual.root")))
        val mode = System.getProperty("morph.visual.mode", "compare")
        val suite = System.getProperty("morph.visual.suite", "quick")
        require(mode in listOf("compare", "candidate"))
        require(suite in listOf("quick", "wide", "infer-wide"))
        val inference = suite == "infer-wide"
        val output = File(root, "morph-playground/build/reports/morph-visual/$suite").apply { mkdirs() }
        val summary = File(output, "summary.json")
        summary.writeText("""{"suite":"$suite","mode":"$mode","state":"running"}""")
        val current = File(output, "candidate").apply { mkdirs() }
        File(current, "validation.txt").delete()
        val before = File(output, "before").apply { mkdirs() }
        val diffs = File(output, "diff").apply { mkdirs() }
        val baseline = File(root, "visual-baselines/$suite")
        fun index(name: String) = iconEntries.indexOfFirst { it.name == name }.also { require(it >= 0) { name } }
        val representatives = listOf("Menu" to "Close", "Search" to "Close", "Close" to "Play",
            "Home" to "Settings", "Lock (Material Outlined)" to "Lock open",
            "Lock open" to "Lock open right", "Lock (Material Outlined)" to "Lock open right",
            "Add" to "Check", "Arrow back" to "Arrow forward", "Favorite" to "Home")
        val quick = representatives.map { (a, b) -> index(a) to index(b) }
            .flatMap { listOf(it, it.second to it.first) }.distinct()
        // Deterministic coverage rather than a prohibitively large all-pairs claim.
        // Probe every catalog entry, then exercise every pair accepted with the probe.
        // Names here define test fixtures only; production eligibility remains geometric.
        val inferredEntries = if (inference) iconEntries.indices.filter { i ->
            buildMorphPlan(iconEntries[i].imageVector, iconEntries[index("Close")].imageVector,
                MorphOptions(transitionMode = MorphTransitionMode.ExperimentalStrokeInference))
                .compatibilityReport.contours.any { it.strategy == MorphAppliedStrategy.InferredCenterline }
        }.plus(index("Close")).distinct() else emptyList()
        val pairs = if (suite == "quick") quick else (quick + iconEntries.indices.flatMap { i ->
            val j = (i + 37) % iconEntries.size
            listOf(i to i, i to j, j to i)
        } + inferredEntries.flatMap { a -> inferredEntries.map { b -> a to b } }).distinct()
        File(output, "inference-coverage.txt").writeText(if (inference)
            "Catalog entries: ${iconEntries.size}\nProbe-compatible entries: ${inferredEntries.size}\n" +
                inferredEntries.joinToString("\n") { "$it\t${iconEntries[it].name}" }
            else "Transition mode: Auto\n")
        val rotations = if (suite == "quick") listOf(MorphRotationPreference.Auto) else MorphRotationPreference.entries
        val animationMode = System.getProperty("morph.visual.animation", "sample")
        require(animationMode in listOf("none", "sample", "all"))
        val animatedPairs = listOf("Search" to "Close", "Home" to "Settings", "Lock (Material Outlined)" to "Lock open right")
            .map { (a, b) -> index(a) to index(b) }.flatMap { listOf(it, it.second to it.first) }.toSet()
        val animations = File(output, "animation").apply { mkdirs() }
        val results = mutableListOf<Result>()
        val manifest = mutableListOf("morph-visual-v1; pixels=$pixels; samples=64; progress=$progress; suite=$suite")
        val previousManifest = File(baseline, "manifest.txt").takeIf { it.isFile }?.readLines()
        RemoteVisualBaseline(baseline, File(root, ".cache/image")).use { references ->
        for ((a, b) in pairs) {
        // The inference matrix is exhaustive over probe-compatible pairs. Catalog fallback
        // pairs use the default policy/direction/interpolation; Auto-wide remains separate.
        val inferenceMatrix = inference && a in inferredEntries && b in inferredEntries
        val policies = if (inference && !inferenceMatrix) listOf(MorphContourStrategy.SharedBoundary) else MorphContourStrategy.entries
        val directions = if (inference && !inferenceMatrix) listOf(MorphRotationPreference.Auto) else rotations
        val interpolations = if (inference && !inferenceMatrix) listOf(MorphInterpolation.Polar) else MorphInterpolation.entries
        for (policy in policies) for (rotation in directions) {
            val options = MorphOptions(contourStrategy = policy, rotationPreference = rotation,
                transitionMode = if (inference) MorphTransitionMode.ExperimentalStrokeInference else MorphTransitionMode.Auto)
            val planning = runCatching { buildMorphPlan(iconEntries[a].imageVector, iconEntries[b].imageVector, options) }
            for (interpolation in interpolations) {
                val id = "${a}_${b}_${policy}_${rotation}_$interpolation"
                val title = "${iconEntries[a].name} → ${iconEntries[b].name} | $policy | $rotation | $interpolation"
                manifest += "$id\t$title"
                val displayTitle = "${iconLabel(a)} → ${iconLabel(b)} | ${label(policy.name)} | ${label(rotation.name)} | ${label(interpolation.name)}"
                try {
                val plan = planning.getOrThrow()
                val frames = progress.map { render(plan, it, interpolation) }
                val animated = animationMode == "all" || (animationMode == "sample" && (a to b) in animatedPairs)
                if (animated) {
                    val sequence = (0..30).map { frame ->
                        if (frame == 0) frames.first() else if (frame == 30) frames.last()
                        else render(plan, frame / 30f, interpolation)
                    }
                    File(animations, "$id.png").writeBytes(ApngEncoder.encode(pixels, pixels, sequence,
                        (0..30).map { frame -> (if (frame == 0) 10 else if (frame == 30) 9 else 1) to 30 }))
                    ImageIO.write(filmstrip(listOf(frames.first())), "png", File(animations, "${id}_poster.png"))
                }
                val near = listOf(render(plan, 0.000001f, interpolation), render(plan, 0.999999f, interpolation))
                val endpointError = coverageDifference(frames.first(), near.first()) + coverageDifference(frames.last(), near.last())
                val areas = frames.map { bitmap -> bitmap.count { it > 128 } }
                val areaJump = areas.zipWithNext().maxOf { (x, y) -> abs(x - y) }
                val sharedDrift = frames.drop(1).dropLast(1).maxOf { frame ->
                    frame.indices.count { i -> (frames.first()[i] > 128) == (frames.last()[i] > 128) &&
                        (frame[i] > 128) != (frames.first()[i] > 128) }
                }
                val holes = frames.map(::holes)
                val holeChanges = holes.zipWithNext().sumOf { (x, y) -> abs(x - y) }
                val film = filmstrip(frames)
                ImageIO.write(film, "png", File(current, "$id.png"))
                val reference = references.image(id)
                var changed = 0
                val status = if (reference == null) "NEW" else {
                    reference.copyTo(File(before, "$id.png"), overwrite = true)
                    val old = ImageIO.read(reference)
                    if (old.width != film.width || old.height != film.height) {
                        changed = film.width * film.height
                    } else {
                        val diff = BufferedImage(film.width, film.height, BufferedImage.TYPE_INT_RGB)
                        for (y in 0 until film.height) for (x in 0 until film.width) {
                            val delta = abs((old.getRGB(x, y) and 255) - (film.getRGB(x, y) and 255))
                            if (delta > 16) changed++
                            diff.setRGB(x, y, if (delta > 16) 0xff0099 else 0xeeeeee)
                        }
                        ImageIO.write(diff, "png", File(diffs, "$id.png"))
                    }
                    if (changed > 0) "CHANGED" else "SAME"
                }
                val trace = plan.compatibilityReport.contours.joinToString("\n") {
                    "轮廓 ${it.index}：${it.sourceContours} → ${it.targetContours}；${label(it.strategy.name)}；" +
                        "公共锚点 ${it.sharedAnchorCount}，连接缝 ${it.holeOpeningCount}；${decisionLabel(it)}" +
                            if (inference) "；${it.decision}" else ""
                }
                results += Result(id, displayTitle, status, changed, endpointError, areaJump, sharedDrift, holeChanges, trace, animated)
                } catch (error: Exception) {
                    error.printStackTrace()
                    results += Result(id, displayTitle, "ERROR", 0, 0, 0, 0, 0, "规划、渲染或基线图片加载失败，请查看桌面测试日志定位异常。")
                }
            }
        }
        }
        }
        File(current, "manifest.txt").writeText(manifest.joinToString("\n") + "\n")
        File(current, "environment.txt").writeText("OS=${System.getProperty("os.name")}\nJava=${System.getProperty("java.version")}\n")
        val manifestChanged = previousManifest != manifest
        val sorted = results.sortedWith(compareByDescending<Result> { it.status != "SAME" }
            .thenByDescending { it.changed }.thenByDescending { it.endpoint }.thenByDescending { it.sharedDrift })
        File(output, "metrics.tsv").writeText("case\tstatus\tchangedPixels\tendpointError\tmaxAreaStep\tendpointAgreedPixelDrift\tholeTransitions\n" +
            sorted.joinToString("\n") { "${it.id}\t${it.status}\t${it.changed}\t${it.endpoint}\t${it.areaJump}\t${it.sharedDrift}\t${it.holeChanges}" })
        File(output, "index.html").writeText(report(sorted, mode, suite, manifestChanged))
        val passed = results.none { it.status == "ERROR" } && results.all { it.endpoint <= 8 } &&
            (mode != "compare" || (!manifestChanged && results.all { it.status == "SAME" }))
        summary.writeText("""{"suite":"$suite","mode":"$mode","state":"complete","generatedAt":"${java.time.Instant.now()}","passed":$passed,"cases":${results.size},"changed":${results.count { it.status != "SAME" }},"manifestChanged":$manifestChanged}""")
        println("Visual report: ${File(output, "index.html").absolutePath}")
        assertTrue(results.none { it.status == "ERROR" }, "Planning/rendering failed; inspect visual report.")
        assertTrue(results.all { it.endpoint <= 8 }, "Endpoint discontinuity detected; inspect visual report.")
        File(current, "validation.txt").writeText("PASS\nEndpoint contracts passed for ${results.size} cases. Visual review remains required.\n")
        if (mode == "compare") assertTrue(!manifestChanged && results.all { it.status == "SAME" },
            "Visual changes or missing baseline; inspect report. Baselines were not updated.")
    }

    private fun render(plan: ImageVectorMorphPlan, t: Float, mode: MorphInterpolation): IntArray {
        val image = renderComposeScene(pixels, pixels) {
            MorphIcon(plan, t, Modifier.size(pixels.dp), tint = Color.Black, interpolation = mode)
        }
        try {
            val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val bitmap = ImageIO.read(ByteArrayInputStream(data.bytes))
                return bitmap.getRGB(0, 0, pixels, pixels, null, 0, pixels).map { it ushr 24 }.toIntArray()
            } finally { data.close() }
        } finally { image.close() }
    }

    private fun coverageDifference(a: IntArray, b: IntArray) = a.indices.count { abs(a[it] - b[it]) > 128 }

    private fun filmstrip(frames: List<IntArray>) = BufferedImage(pixels * frames.size, pixels, BufferedImage.TYPE_INT_RGB).apply {
        frames.forEachIndexed { frame, bitmap -> for (y in 0 until pixels) for (x in 0 until pixels) {
            val v = 255 - bitmap[y * pixels + x]
            setRGB(frame * pixels + x, y, (v shl 16) or (v shl 8) or v)
        } }
    }

    /** Background components enclosed by ink; a diagnostic, not a quality verdict. */
    private fun holes(alpha: IntArray): Int {
        val visited = BooleanArray(alpha.size)
        val queue = IntArray(alpha.size)
        var holes = 0
        for (start in alpha.indices) {
            if (visited[start] || alpha[start] > 128) continue
            var head = 0
            var tail = 1
            queue[0] = start
            visited[start] = true
            var boundary = false
            while (head < tail) {
                val i = queue[head++]
                val x = i % pixels
                val y = i / pixels
                if (x == 0 || y == 0 || x == pixels - 1 || y == pixels - 1) boundary = true
                fun visit(next: Int) {
                    if (next in alpha.indices && !visited[next] && alpha[next] <= 128) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
                if (x > 0) visit(i - 1)
                if (x + 1 < pixels) visit(i + 1)
                visit(i - pixels)
                visit(i + pixels)
            }
            if (!boundary && tail > 2) holes++
        }
        return holes
    }

    // Display labels are independent of the stable baseline manifest and machine-readable fields.
    private fun iconLabel(index: Int): String = when (iconEntries[index].name) {
        "Menu" -> "菜单"
        "Close" -> "关闭"
        "Search" -> "搜索"
        "Play" -> "播放"
        "Home" -> "主页"
        "Settings" -> "设置"
        "Lock (Material Outlined)" -> "锁定（材质描边）"
        "Lock open" -> "解锁"
        "Lock open right" -> "向右解锁"
        "Add" -> "添加"
        "Check" -> "勾选"
        "Arrow back" -> "返回箭头"
        "Arrow forward" -> "前进箭头"
        "Favorite" -> "收藏"
        else -> "图标 $index"
    }

    private fun label(value: String): String = when (value) {
        "Standard" -> "标准轮廓"
        "SharedBoundary" -> "公共边界"
        "ExperimentalHoleOpening" -> "实验性孔洞连接"
        "Centerline" -> "笔画中心线"
        "Outline" -> "普通轮廓"
        "Collapse" -> "收缩或展开"
        "Auto" -> "自动旋转"
        "PreferClockwise" -> "顺时针"
        "PreferCounterClockwise" -> "逆时针"
        "Polar" -> "极坐标插值"
        "Linear" -> "线性插值"
        "SAME" -> "一致"
        "CHANGED" -> "有变化"
        "NEW" -> "新增"
        "ERROR" -> "异常"
        "quick" -> "快速回归"
        "wide" -> "广域抽样"
        "infer-wide" -> "笔画推断广域抽样"
        "compare" -> "基线对比"
        "candidate" -> "候选生成"
        else -> "未识别类型"
    }

    private fun decisionLabel(report: MorphContourReport): String = when (report.strategy) {
        MorphAppliedStrategy.Collapse -> when {
            report.sourceContours.isEmpty() -> "缺少源轮廓，采用线性展开。"
            report.targetContours.isEmpty() -> "缺少目标轮廓，采用线性收缩。"
            else -> "轮廓未能匹配，采用线性收缩或展开。"
        }
        MorphAppliedStrategy.Centerline -> "仅涉及笔画对应，不应用填充轮廓策略。"
        MorphAppliedStrategy.InferredCenterline -> "实验性笔画识别；核对原始轮廓的重建误差后使用中心线。"
        MorphAppliedStrategy.ExperimentalHoleOpening -> "已显式启用实验策略；局部未匹配孔洞具有唯一合格父轮廓和有效连接缝。"
        MorphAppliedStrategy.SharedBoundary -> "顺序一致的公共片段支持 ${report.sharedAnchorCount} 个边界锚点。"
        MorphAppliedStrategy.Outline -> if (report.decision.startsWith("Standard policy:"))
            "标准策略：已关闭公共边界与拓扑启发式规则。"
            else "没有合格公共边界（存在歧义、顺序变化或原始几何信息不足），采用标准求解。"
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun report(results: List<Result>, mode: String, suite: String, manifestChanged: Boolean): String = """
        <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>变形动画视觉回归报告</title>
        <style>body{font:14px system-ui;margin:24px;background:#f6f7f9;color:#182033}input{padding:10px;width:480px;max-width:100%;box-sizing:border-box}article{background:white;margin:20px 0;padding:16px;border:1px solid #ccd}img{width:480px;max-width:100%;border:1px solid #ddd}pre{white-space:pre-wrap}small{display:block} .images{display:flex;gap:16px;flex-wrap:wrap}</style>
        <h1>变形动画视觉回归 · ${label(suite)} · ${label(mode)}</h1>
        <p>${results.size} 个序列 · ${results.count { it.status == "CHANGED" }} 项变化 · ${results.count { it.status == "NEW" }} 项新增 · 清单变化：${if (manifestChanged) "是" else "否"}</p>
        <p>每组图片依次展示 0%、25%、50%、75%、100% 的动画进度。指标用于提示需要审阅的案例，不能直接判断视觉效果是否合理。标准轮廓策略单独列为对比案例。生成候选报告不会更新已保存的基线。</p>
        <div style="background:white;border:1px solid #ccd;padding:16px;margin:16px 0;line-height:1.8">
        <strong>如何阅读差异图</strong>
        <div>差异图比较的是<strong>当前结果与历史基线的同一进度帧</strong>，不是动画起点与终点。</div>
        <div><span style="display:inline-block;width:14px;height:14px;background:#eeeeee;border:1px solid #bbb;vertical-align:middle"></span> 灰色（#eeeeee）：该像素没有差异，或差异未超过通道容差 16/255。整张灰色表示没有超出容差的变化，不是渲染失败。</div>
        <div><span style="display:inline-block;width:14px;height:14px;background:#ff0099;vertical-align:middle"></span> 粉色（#ff0099）：该像素与历史基线的通道差异超过 16/255，需要审阅。粉色标记差异位置，不表示变化一定是错误。</div>
        <div>「一致」表示全部像素均在容差内；「有变化」表示存在超出容差的像素或图片尺寸不同；「新增」表示没有历史基线，因此不提供差异图；「异常」表示本次验证未完成，请查看规划说明与测试日志，不能视为通过。</div>
        </div>
        <p>方形动画预览：${results.count { it.animated }} 个案例，96×96 像素，单程约 1 秒、每秒 30 帧，首尾停留约 300 毫秒后重新播放。仅播放可见区域中的预览；它用于观察运动，不代表应用实际运行帧率，也不参与基线像素判定。</p>
        <input id="query" placeholder="筛选图标、策略或状态"><label><input id="changed" type="checkbox" style="width:auto">仅显示变化、新增或异常</label>
        ${results.joinToString("\n") { row -> """<article data-status="${row.status}"><h3>${escape(row.title)} · ${label(row.status)}</h3>
        <p>变化像素 ${row.changed}；端点误差 ${row.endpoint}；最大帧间面积变化 ${row.areaJump}；端点一致区域的中途像素偏移 ${row.sharedDrift}；孔洞数量变化 ${row.holeChanges}</p>
        <div class="images">${if (row.status == "NEW") "" else "<div><small>已保存基线</small><img loading=lazy src='before/${row.id}.png'></div>"}
        <div><small>当前结果</small><img loading=lazy src="candidate/${row.id}.png"></div>
        ${if (row.animated) "<div><small>动画预览</small><img class='animation' loading='lazy' width='96' height='96' style='width:96px;height:96px' src='animation/${row.id}_poster.png' data-animation='animation/${row.id}.png'></div>" else ""}
        ${if (row.status == "NEW") "" else "<div><small>差异图（通道容差 16/255）</small><img loading=lazy src='diff/${row.id}.png'></div>"}</div>
        <details><summary>规划决策</summary><pre>${escape(row.trace)}</pre></details></article>""" }}
        <script>function filter(){const q=document.querySelector('#query').value.toLowerCase();const only=document.querySelector('#changed').checked;document.querySelectorAll('article').forEach(e=>e.hidden=!e.textContent.toLowerCase().includes(q)||(only&&e.dataset.status==='SAME'));}document.querySelectorAll('input').forEach(e=>e.addEventListener('input',filter));const visible=new Set();function syncAnimation(e){const next=!document.hidden&&visible.has(e)?e.dataset.animation:e.dataset.poster;if(e.getAttribute('src')!==next)e.setAttribute('src',next);}const observer=new IntersectionObserver(entries=>entries.forEach(({target,isIntersecting})=>{if(isIntersecting)visible.add(target);else visible.delete(target);syncAnimation(target);}));document.querySelectorAll('img.animation').forEach(e=>{e.dataset.poster=e.getAttribute('src');observer.observe(e);});document.addEventListener('visibilitychange',()=>document.querySelectorAll('img.animation').forEach(syncAnimation));</script></html>
    """.trimIndent()
}
