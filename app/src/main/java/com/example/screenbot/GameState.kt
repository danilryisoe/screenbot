package com.example.screenbot

enum class GameState(val label: String) {
    LOBBY("Лобби — жму Играть"),
    MATCHMAKING("Поиск игроков..."),
    IN_BATTLE("В бою"),
    END_SCREEN("Итоги матча — выход"),
    POPUP("Всплывающее окно / Награда"),
    UNKNOWN("Определение экрана...")
}

enum class BotMode(val title: String) {
    SHOWDOWN_FARM("ШД: Фарм + уклон от яда"),
    AGGRESSIVE("Агрессивный пуш + ульта"),
    ANTI_AFK("Тихий анти-АФК")
}
