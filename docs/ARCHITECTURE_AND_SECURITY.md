# Техническая архитектура / безопасность

> Историческая спецификация до этапа 2. Требования об основном серверном хранилище и исключительно серверном ключе Token Harbor заменены новым заданием пользователя. Актуальная архитектура: `STAGE_2_ARCHITECTURE.md`; настройка, безопасность BYOK и ограничения синхронизации: `STAGE_2_SETUP.md`. Room — основной источник в гостевом режиме и аккаунте; BYOK допускает HTTPS напрямую только к Token Harbor, без сохранения ключа в Supabase. Ниже сохранён исходный план для контекста будущих этапов.

## 1. Клиент
- Android `minSdk 26` (совместимость с текущей документацией supabase-kt), target/compile SDK — актуальные стабильные на момент сборки, с обязательной проверкой совместимости.
- Kotlin + Jetpack Compose + Navigation Compose + Material3 (кастомная тема), MVVM или UDF, ViewModel + StateFlow, Coroutines.
- Рекомендуемая структура:
```
app/
  core/designsystem/       # цветовые токены, шрифты, навигация, анимации
  core/network/            # Supabase client и безопасные ошибки
  core/model/              # Capture, ProposedItem, Item, Project, UserSettings
  data/auth/               # session/auth repository
  data/capture/            # cloud capture repository, pending queue
  data/items/              # item CRUD, project filters
  data/ai/                 # вызовы Edge Function, парсинг ответа
  data/speech/             # SpeechRecognizer adapter, callback -> Flow
  feature/onboarding/
  feature/auth/
  feature/capture/
  feature/tasks/
  feature/thoughts/
  feature/calendar/
  feature/overview/
  feature/settings/
```
- `DataStore` для темы/палитры и несекретных настроек; Supabase Postgres — постоянные пользовательские записи. Черновик текста сохранять локально до успешной отправки (DataStore/Room), не терять при повороте/закрытии.
- Диалоговое состояние и список предложений должны переживать конфигурационные изменения; server source of truth для подтверждённых записей. Для устойчивого офлайн-режима позже можно подключить Room + sync; **не обещать офлайн-синхронизацию, пока она не реализована**.

## 2. Backend
Supabase Auth (email+password), таблицы `profiles`, `captures`, `items`, `weekly_reviews`. См. `supabase/migrations/001_core.sql`.
- `captures`: неизменяемый по смыслу исходник речи/текста, пользователь, статус `inbox|committed`, ссылка на родительский capture для фрагментов `inbox`.
- `items`: подтверждённые структурированные сущности (`action`, `project`, `thought`, `event`, `reference`), происхождение `capture_id`, статус действия, тип мысли, сведения календаря.
- `weekly_reviews`: пользователь и дата завершения.
- UI-фильтры: next/waiting/someday/done для action; idea/reflection для thought; event для календаря; project для списка проектов.

**Обязательно** RLS по `user_id = auth.uid()` на каждой таблице, ограничения FK и отдельный тест межпользовательской изоляции. Миграция даёт стартовый вариант; агент должен прогнать её в тестовом проекте Supabase.

## 3. AI через Token Harbor
```
Android (JWT Supabase Auth)
        ↓ POST /functions/v1/analyze-capture
Supabase Edge Function (проверяет пользователя и лимиты)
        ↓ HTTPS Authorization: Bearer <TOKEN_HARBOR_SECRET>
Token Harbor /v1/... (проверить доступные маршруты, модель)
        ↓ JSON/текст
Edge Function валидирует структуру → возвращает proposals
        ↓
Android показывает предложения, пользователь редактирует
        ↓
Supabase RPC confirm_capture() → одна транзакция
```
- TOKEN_HARBOR_API_KEY только в Supabase Edge Function Secrets, никогда в APK/BuildConfig/local.properties, доступном в релизе, или Git.
- `TOKEN_HARBOR_MODEL` — серверная настройка; конкретное имя модели не задано пользователем и не должно выдумываться.
- Не полагайся без проверки на `json_schema`/structured outputs: OpenAI-compatible формат не означает поддержку всех опций у каждой модели. Дай модели строгую инструкцию вернуть JSON, обязательно проверь ответ на сервере, предусмотрев понятную ошибку/повтор.
- Проверь авторизацию вызова Edge Functions и совместимость с версией SDK, используй валидный пользовательский JWT, не отключай проверку JWT ради обхода 401.
- Лимиты: max input, max number of suggestions, timeout, retry с backoff, не логируй полный текст личных мыслей.
- Подсказка первого шага — отдельный AI-usecase, который ничего не записывает до подтверждения.

## 4. Транскрибация Android
- `android.speech.SpeechRecognizer`, `RecognitionListener`; `RECORD_AUDIO` runtime permission; `isRecognitionAvailable`; русская локаль `ru-RU`.
- `onPartialResults` показывает промежуточную фразу без дублирования в финальном тексте; `onResults` заменяет промежуточный фрагмент финальным; `onError` восстанавливает UI и не стирает уже записанное.
- `destroy()` на lifecycle cleanup; вызовы на главном потоке, правильный `RECORD_AUDIO` и `<queries>` для RecognitionService там, где требуется.
- `createOnDeviceSpeechRecognizer` только при поддержке on-device на API 31+, иначе обычный системный сервис (может пользоваться сетью). Пользователю честно объяснять передачу речи системной службе, когда применимо.
- Не хранить сырой аудиофайл и не отправлять его в Token Harbor. По умолчанию только распознанный и подтверждённый пользователем текст.

## 5. Конфиденциальность
- Данные дневника могут быть чувствительными. Не использовать их для рекламы/аналитики и не помещать целиком в crash logs.
- Только пользователь видит свои записи. Перед отправкой текста для AI показать согласие/прозрачную подпись «Текст будет отправлен сервису ИИ для анализа»; разрешить вариант «На потом» без ИИ.
- Не отправлять в AI чужие записи или историю без явной необходимости/предупреждения.
- Пользователь может удалить исходные записи и связанные подтверждённые элементы; обдумать явный выбор «только исходник»/«исходник и результаты».
- Надёжно обработать выход из аккаунта: очистить персональные локальные кеши/черновики или разделить их по userId.

## 6. Контроль качества
- Unit tests: классификация ответа/парсер, mapper route->entity, дата, duplicate confirm protection, speech partial/final state.
- Instrumentation/UI tests: auth, пять экранов, запись/изменение/подтверждение, long text, empty states, Android Back, keyboard.
- Supabase SQL/RLS tests: пользователь A не может читать/писать B; anon не имеет CRUD; повторный `confirm_capture` не дублирует элементы.
- Реальный Android: проверить голос, фон/возвращение, отказ разрешения микрофона, сеть off/on.

## 7. Актуальные официальные источники для проверки при реализации
- https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
- https://supabase.com/docs/reference/kotlin/installing
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.com/docs/guides/functions/auth
- https://supabase.com/docs/guides/functions/secrets
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/topic/architecture/recommendations
- https://tokenharbor.ai/faq
- https://tokenharbor.ai/docs/connect
