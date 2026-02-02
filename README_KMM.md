# Android App

Android додаток для моніторингу стану інвертора.

## Структура проекту

```
boiller_app/
└── app/                    # Android app модуль
    └── src/main/java/      # Код додатку
        ├── api/            # API клієнт та моделі даних
        ├── settings/        # Налаштування
        ├── utils/          # Утиліти
        └── websocket/       # WebSocket сервіс
```

## Компоненти

- **API клієнт** (`ApiService`) - використовує Ktor для HTTP запитів
- **Моделі даних** (`DataRecord`, `DataResponse`)
- **Утиліти** (`DateFormatter`, `LightChangeEvent`)
- **Налаштування** (`Settings`) - для зберігання налаштувань
- **WebSocket сервіс** - для отримання реального часу оновлень

## Білд проекту

```bash
./gradlew :app:assembleDebug
```

## Залежності

- **Ktor** - для HTTP запитів
- **Kotlinx Serialization** - для серіалізації JSON
- **Kotlinx DateTime** - для роботи з датами
- **Kotlinx Coroutines** - для асинхронних операцій
- **MPAndroidChart** - для відображення графіків
- **Socket.IO** - для WebSocket з'єднань
