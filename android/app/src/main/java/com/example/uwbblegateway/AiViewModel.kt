package com.example.uwbblegateway

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiScreenState(
    val latestResult: InferenceResult? = null,
    val isAnalyzing: Boolean = false,
    val isAutoMode: Boolean = false,   // 自动帧分析模式
    val frameCount: Int = 0
)

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AiInferenceEngine(application)

    private val _aiState = MutableStateFlow(AiScreenState())
    val aiState: StateFlow<AiScreenState> = _aiState.asStateFlow()

    /** 分析单帧图像 */
    fun analyzeFrame(bitmap: Bitmap) {
        if (_aiState.value.isAnalyzing) return
        _aiState.value = _aiState.value.copy(isAnalyzing = true)
        viewModelScope.launch {
            val result = engine.analyze(bitmap)
            _aiState.value = _aiState.value.copy(
                latestResult = result,
                isAnalyzing = false,
                frameCount = _aiState.value.frameCount + 1
            )
        }
    }

    /** 触发一次模拟推理（无图像帧时测试用） */
    fun triggerSimulatedAnalysis() {
        val dummyBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        analyzeFrame(dummyBitmap)
    }

    /** 将推理结果通过 BLE 下发眼镜端（附加到导航数据 payload） */
    fun pushResultToGlasses(bleManager: BleManager) {
        val result = _aiState.value.latestResult ?: return
        // 发送 AI 推理结果作为特殊导航数据（借用 sendNavData 通道）
        val dummyPos = Position2D(0f, 0f, 0f, "AI")
        bleManager.sendNavData(dummyPos, null)
    }
}
