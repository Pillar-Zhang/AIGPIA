package com.example.uwbblegateway

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val TAG = "AiInferenceEngine"

// ─── 数据模型 ──────────────────────────────────────────────────────────────────

data class DetectionBox(
    val label: String,
    val confidence: Float,
    val bbox: RectF   // 归一化坐标 0~1
)

data class OcrResult(
    val text: String,
    val confidence: Float,
    val isSimulated: Boolean = false
)

data class MeterReading(
    val value: Float,
    val unit: String,
    val meterType: String = "digital", // "digital" / "pointer"
    val isSimulated: Boolean = false
)

data class InferenceResult(
    val boxes: List<DetectionBox> = emptyList(),
    val ocr: OcrResult? = null,
    val meter: MeterReading? = null,
    val isSimulated: Boolean = false,
    val inferenceMs: Long = 0L
)

// ─── 引擎 ──────────────────────────────────────────────────────────────────────

/**
 * 本地 AI 推理引擎（三合一）
 *
 * 1. YOLOv8 目标检测   → assets/models/yolov8_power.tflite
 * 2. OCR 铭牌识别      → assets/models/ocr_nameplate.tflite
 * 3. 表计读数识别      → assets/models/meter_reader.tflite
 *
 * 任意模型文件不存在时，对应功能自动降级为模拟推理（isSimulated = true）。
 */
class AiInferenceEngine(private val context: Context) {

    // 模式检测：检查 assets 中是否有真实模型
    private val hasYoloModel: Boolean by lazy { assetExists("models/yolov8_power.tflite") }
    private val hasOcrModel: Boolean  by lazy { assetExists("models/ocr_nameplate.tflite") }
    private val hasMeterModel: Boolean by lazy { assetExists("models/meter_reader.tflite") }

    // 电力设备标签（YOLOv8 分类）
    private val powerDeviceLabels = listOf(
        "主变压器", "断路器", "隔离开关", "电流互感器",
        "电压互感器", "避雷器", "接地刀闸", "控制柜"
    )

    /**
     * 对单帧图像执行全量推理
     * 在 IO 线程执行，调用方可直接 await
     */
    suspend fun analyze(bitmap: Bitmap): InferenceResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()

        val boxes = if (hasYoloModel) {
            runYoloInference(bitmap)
        } else {
            simulateDetection()
        }

        val ocr = if (hasOcrModel) {
            runOcrInference(bitmap)
        } else {
            simulateOcr()
        }

        val meter = if (hasMeterModel) {
            runMeterInference(bitmap)
        } else {
            simulateMeter()
        }

        val elapsed = System.currentTimeMillis() - start
        val simulated = !hasYoloModel || !hasOcrModel || !hasMeterModel

        Log.d(TAG, "Inference done in ${elapsed}ms, simulated=$simulated, boxes=${boxes.size}")

        InferenceResult(
            boxes = boxes,
            ocr = ocr,
            meter = meter,
            isSimulated = simulated,
            inferenceMs = elapsed
        )
    }

    // ─── 真实推理（占位，接入 TFLite Interpreter） ────────────────────────────

    private fun runYoloInference(bitmap: Bitmap): List<DetectionBox> {
        // TODO: 加载 TFLite Interpreter，执行推理，解析输出张量
        // val interpreter = Interpreter(loadModelFile("models/yolov8_power.tflite"))
        return emptyList()
    }

    private fun runOcrInference(bitmap: Bitmap): OcrResult {
        // TODO: OCR 推理
        return OcrResult("", 0f, false)
    }

    private fun runMeterInference(bitmap: Bitmap): MeterReading {
        // TODO: 表计推理
        return MeterReading(0f, "kV", isSimulated = false)
    }

    // ─── 模拟推理（无模型时完整演示） ────────────────────────────────────────

    private fun simulateDetection(): List<DetectionBox> {
        val count = Random.nextInt(1, 4)
        return (0 until count).map {
            val x1 = Random.nextFloat() * 0.5f
            val y1 = Random.nextFloat() * 0.5f
            DetectionBox(
                label = powerDeviceLabels.random(),
                confidence = 0.75f + Random.nextFloat() * 0.22f,
                bbox = RectF(x1, y1, x1 + 0.2f + Random.nextFloat() * 0.2f,
                             y1 + 0.15f + Random.nextFloat() * 0.15f)
            )
        }
    }

    private fun simulateOcr(): OcrResult {
        val brands = listOf("SJ-110kV/5A", "ABB-GIS-220", "西门子-3AP1-FG", "许继电气-LW36-126")
        return OcrResult(
            text = "型号: ${brands.random()}\n编号: SN-${Random.nextInt(10000, 99999)}",
            confidence = 0.88f + Random.nextFloat() * 0.1f,
            isSimulated = true
        )
    }

    private fun simulateMeter(): MeterReading {
        return MeterReading(
            value = (Random.nextFloat() * 110f + 5f).let { "%.1f".format(it).toFloat() },
            unit = listOf("kV", "A", "MW", "kW").random(),
            meterType = if (Random.nextBoolean()) "digital" else "pointer",
            isSimulated = true
        )
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
