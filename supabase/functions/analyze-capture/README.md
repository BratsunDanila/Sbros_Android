# Edge Function `analyze-capture` — контракт реализации

Эта папка намеренно содержит контракт, а не «фиктивно готовую интеграцию».

Реализовать TypeScript/Deno функцию с аутентификацией пользователя по Supabase JWT и проверкой запроса. Она получает `{capture_id}` (лучше, чем сырой текст от клиента, т.к. запись уже сохранена), по `ctx.supabase` читает **только свою** запись, вызывает Token Harbor HTTPS по OpenAI-compatible API, валидирует модельный JSON, возвращает `{proposals: [...]}`.

**Не сохранять предложения как задачи до подтверждения в Android**. Подтверждение — `public.confirm_capture(p_capture_id, p_proposals)` из SQL.

Secrets в Edge Function: `TOKEN_HARBOR_API_KEY`, `TOKEN_HARBOR_MODEL` (конкретный ID взять из актуального каталога). API base URL проверить, ожидаемый https://tokenharbor.ai/v1.

Обработать 400/401/403/408/429/5xx; лимит размера, throttling, таймаут, повтор. Не выводить полный пользовательский текст в лог. Не записывать в ответ ключ/сырой upstream-ответ. Проверить доступность JSON mode конкретной модели; валидировать объект/массив независимо от ответа провайдера.

Подробно: `docs/AI_INSTRUCTIONS.md`, `contracts/analysis_response.example.json`, `docs/ARCHITECTURE_AND_SECURITY.md`.
