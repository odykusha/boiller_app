# Kotlin Multiplatform Mobile (KMM) Setup

Проект було переписано під Kotlin Multiplatform Mobile для підтримки Android та iOS.

## Структура проекту

```
boiller_app/
├── app/                    # Android app модуль
│   └── src/main/java/      # Android-специфічний код (UI, сервіси)
├── shared/                 # Спільний модуль для Android та iOS
│   └── src/
│       ├── commonMain/     # Спільний код
│       ├── androidMain/     # Android-специфічна реалізація
│       └── iosMain/         # iOS-специфічна реалізація
└── iosApp/                 # iOS проект (потрібно створити)
```

## Що винесено в shared модуль

- **API клієнт** (`ApiService`) - використовує Ktor замість Retrofit
- **Моделі даних** (`DataRecord`, `DataResponse`)
- **Утиліти** (`DateFormatter`)
- **Налаштування** (`Settings`) - expect/actual для платформо-специфічного зберігання

## Наступні кроки для iOS

1. Створити iOS проект в Xcode
2. Додати shared модуль як залежність
3. Створити iOS UI (SwiftUI або UIKit)
4. Використати shared модуль для бізнес-логіки

## Білд проекту

### Android
```bash
./gradlew :app:assembleDebug
```

### iOS (після створення iOS проекту)
```bash
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

## Залежності

- **Ktor** - для HTTP запитів (замість Retrofit)
- **Kotlinx Serialization** - для серіалізації JSON
- **Kotlinx DateTime** - для роботи з датами
- **Kotlinx Coroutines** - для асинхронних операцій
