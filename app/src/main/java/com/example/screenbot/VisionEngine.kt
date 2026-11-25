package com.example.screenbot

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.hypot

data class FrameAnalysis(
    val state: GameState,
    val nearestEnemy: PointF?,      // Нормализованные координаты (0..1) ближайшего врага
    val enemyDistance: Float,       // Дистанция до врага (0..1)
    val dangerVector: PointF,       // Вектор от яда/опасности (куда отступать)
    val isSuperReady: Boolean       // Заряжена ли жёлтая кнопка ульты
)

class VisionEngine {

    private val hsvBuffer = FloatArray(3)

    fun analyze(bmp: Bitmap): FrameAnalysis {
        val w = bmp.width
        val h = bmp.height

        // 1. Проверяем кнопку "Играть" / "Выйти" в правом нижнем углу (ярко-жёлтая)
        val playPixel = bmp.getPixel((w * 0.89f).toInt(), (h * 0.88f).toInt())
        if (isPlayOrExitYellow(playPixel)) {
            // Отличаем лобби от экрана завершения матча по центру экрана
            val centerPixel = bmp.getPixel((w * 0.50f).toInt(), (h * 0.18f).toInt())
            val state = if (isBrightBanner(centerPixel)) GameState.END_SCREEN else GameState.LOBBY
            return FrameAnalysis(state, null, 1f, PointF(0f, 0f), false)
        }

        // 2. Проверяем наличие джойстиков / кнопок боя (правый нижний сектор)
        val attackZonePixel = bmp.getPixel((w * 0.86f).toInt(), (h * 0.74f).toInt())
        val isInBattle = isBattleHudVisible(bmp, w, h) || isRedAttackButton(attackZonePixel)

        if (!isInBattle) {
            // Проверяем, не идёт ли поиск матча (тёмный экран с текстом по центру)
            val topBar = bmp.getPixel((w * 0.5f).toInt(), (h * 0.08f).toInt())
            val state = if (Color.red(topBar) < 45 && Color.green(topBar) < 45) {
                GameState.MATCHMAKING
            } else {
                GameState.POPUP
            }
            return FrameAnalysis(state, null, 1f, PointF(0f, 0f), false)
        }

        // 3. Мы в бою — сканируем сетку экрана с шагом для скорости (меньше 15 мс на кадр)
        var bestEnemy: PointF? = null
        var minDist = Float.MAX_VALUE
        var poisonX = 0f
        var poisonY = 0f

        val stepX = (w / 48).coerceAtLeast(8)
        val stepY = (h / 28).coerceAtLeast(8)

        var y = (h * 0.12f).toInt()
        val maxY = (h * 0.85f).toInt()
        val minX = (w * 0.10f).toInt()
        val maxX = (w * 0.90f).toInt()

        while (y < maxY) {
            var x = minX
            while (x < maxX) {
                val c = bmp.getPixel(x, y)
                val nx = x.toFloat() / w
                val ny = y.toFloat() / h

                // Поиск красных полосок HP врагов / ящиков
                if (isEnemyHpRed(c)) {
                    val dist = hypot(nx - 0.5f, ny - 0.5f)
                    if (dist < minDist && dist > 0.04f) {
                        minDist = dist
                        bestEnemy = PointF(nx, ny)
                    }
                }

                // Поиск ядовитого газа ШД по периметру (ядовито-зелёный)
                if ((nx < 0.25f || nx > 0.75f || ny < 0.25f || ny > 0.75f) && isPoisonGreen(c)) {
                    poisonX += (0.5f - nx)
                    poisonY += (0.5f - ny)
                }

                x += stepX
            }
            y += stepY
        }

        // 4. Проверяем готовность ульты (жёлтое свечение кнопки Super)
        val superPixel = bmp.getPixel((w * 0.75f).toInt(), (h * 0.78f).toInt())
        val superReady = isSuperYellow(superPixel)

        return FrameAnalysis(
            state = GameState.IN_BATTLE,
            nearestEnemy = bestEnemy,
            enemyDistance = if (bestEnemy != null) minDist else 1f,
            dangerVector = PointF(poisonX, poisonY),
            isSuperReady = superReady
        )
    }

    private fun isBattleHudVisible(bmp: Bitmap, w: Int, h: Int): Boolean {
        // В бою в верхнем левом углу есть индикатор игроков/счёта
        val p = bmp.getPixel((w * 0.06f).toInt(), (h * 0.07f).toInt())
        return Color.alpha(p) > 0 && (Color.red(p) > 180 || Color.blue(p) > 180)
    }

    private fun isPlayOrExitYellow(c: Int): Boolean {
        Color.colorToHSV(c, hsvBuffer)
        return hsvBuffer[0] in 38f..56f && hsvBuffer[1] > 0.65f && hsvBuffer[2] > 0.75f
    }

    private fun isSuperYellow(c: Int): Boolean {
        Color.colorToHSV(c, hsvBuffer)
        return hsvBuffer[0] in 42f..60f && hsvBuffer[1] > 0.75f && hsvBuffer[2] > 0.80f
    }

    private fun isRedAttackButton(c: Int): Boolean {
        Color.colorToHSV(c, hsvBuffer)
        return (hsvBuffer[0] < 14f || hsvBuffer[0] > 345f) && hsvBuffer[1] > 0.60f && hsvBuffer[2] > 0.55f
    }

    private fun isEnemyHpRed(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return r > 205 && g in 20..85 && b in 20..85
    }

    private fun isPoisonGreen(c: Int): Boolean {
        Color.colorToHSV(c, hsvBuffer)
        return hsvBuffer[0] in 85f..145f && hsvBuffer[1] > 0.65f && hsvBuffer[2] > 0.45f
    }

    private fun isBrightBanner(c: Int): Boolean {
        return Color.red(c) > 200 && Color.green(c) > 180
    }
}
