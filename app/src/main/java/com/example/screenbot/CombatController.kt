package com.example.screenbot

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class BotAction(
    val moveAngleRad: Float?,       // Угол отклонения левого джойстика (null = стоять)
    val moveDurationMs: Long,
    val shouldAttack: Boolean,
    val shouldUseSuper: Boolean,
    val aimAngleRad: Float?         // Направление прицела (для ульты/атаки)
)

class CombatController {

    private var patrolAngle = Random.nextFloat() * 6.28f
    private var ticksSinceTurn = 0

    fun decide(analysis: FrameAnalysis, mode: BotMode): BotAction {
        if (mode == BotMode.ANTI_AFK) {
            patrolAngle += 1.4f
            return BotAction(patrolAngle, 350L, shouldAttack = true, shouldUseSuper = false, aimAngleRad = null)
        }

        // 1. Приоритет №1: убежать от ядовитого тумана к центру карты
        val poisonMag = hypot(analysis.dangerVector.x, analysis.dangerVector.y)
        if (poisonMag > 0.15f) {
            val safeAngle = atan2(analysis.dangerVector.y, analysis.dangerVector.x)
            return BotAction(
                moveAngleRad = safeAngle,
                moveDurationMs = 650L,
                shouldAttack = analysis.nearestEnemy != null && analysis.enemyDistance < 0.35f,
                shouldUseSuper = false,
                aimAngleRad = null
            )
        }

        // 2. Если в поле зрения есть враг / ящик
        val enemy = analysis.nearestEnemy
        if (enemy != null) {
            val dx = enemy.x - 0.5f
            val dy = enemy.y - 0.5f
            val angleToEnemy = atan2(dy, dx)

            val moveAngle = when {
                // Слишком близко — отступаем (кайт), отстреливаясь
                analysis.enemyDistance < 0.18f -> angleToEnemy + 3.14159f
                // Оптимальная дистанция в режиме фарма — держимся чуть сбоку
                mode == BotMode.SHOWDOWN_FARM && analysis.enemyDistance < 0.30f -> angleToEnemy + 1.57f
                // Иначе сближаемся
                else -> angleToEnemy
            }

            val inRange = analysis.enemyDistance < 0.42f
            return BotAction(
                moveAngleRad = moveAngle,
                moveDurationMs = 500L,
                shouldAttack = inRange,
                shouldUseSuper = inRange && analysis.isSuperReady,
                aimAngleRad = angleToEnemy
            )
        }

        // 3. Врагов рядом нет — плавное патрулирование со сменой курса каждые 3 тика
        ticksSinceTurn++
        if (ticksSinceTurn >= 3) {
            ticksSinceTurn = 0
            patrolAngle += Random.nextFloat() * 2.4f - 1.2f
        }

        return BotAction(
            moveAngleRad = patrolAngle,
            moveDurationMs = 550L,
            shouldAttack = false,
            shouldUseSuper = false,
            aimAngleRad = null
        )
    }

    fun angleToOffset(angleRad: Float, radiusPx: Float): PointF {
        return PointF(
            cos(angleRad) * radiusPx,
            sin(angleRad) * radiusPx
        )
    }
}
