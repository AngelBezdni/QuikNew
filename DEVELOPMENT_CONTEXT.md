# QuikClient - Development Context

## Цель проекта

Java-клиент для подключения к Lua-серверу QUIK (QuikSharp-стек) с:
- RPC-вызовами в QUIK,
- приемом callback-событий,
- сохранением данных в локальную H2,
- UI на Swing для ручного запуска и анализа.

## Текущее состояние

Реализовано:
- Подключение к QUIK# по двум сокетам:
  - `response` (RPC запрос/ответ),
  - `callback` (push-события).
- UI (Swing):
  - подключение/отключение,
  - `Ping`,
  - получение сделок по UID,
  - вкладка `Ответ RPC`,
  - вкладка `Колбеки`,
  - вкладка `Сводка из H2`.
- H2-персистентность:
  - сырые RPC-ответы,
  - callback-события,
  - нормализованные строки сделок,
  - агрегированная сводка по активам.
- Сборка fat-jar (shade) для запуска на другой машине.

## Важное решение по фильтрации UID

Сейчас фильтрация сделок по UID выполняется **на стороне Lua**, а не в Java.

Добавлена Lua-команда:
- `get_trades_by_uid`

Java-клиент вызывает именно ее:
- `GetTradesByUidScript.run(uid)` -> `cmd = get_trades_by_uid`.

Это снижает объем передаваемых данных (не тащим все сделки, потом фильтрация).

## Структура ключевых классов (Java)

- `org.example.Main` - старт UI.
- `org.example.ui.QuikDesktopFrame` - главное окно Swing.
- `org.example.quik.session.QuikSharpSession` - два TCP-сокета (response/callback).
- `org.example.quik.rpc.QuikRpcClient` - синхронный RPC.
- `org.example.quik.callback.CallbackReaderService` - фоновое чтение callback.
- `org.example.scripts.GetTradesByUidScript` - вызов Lua `get_trades_by_uid`.
- `org.example.scripts.PingScript` - ping.
- `org.example.storage.H2QuikRepository` - вся работа с H2.
- `org.example.analytics.TradeRowParser` - разбор `sec_code`, `qty`, `flags`, `price`, `value`, side.
- `org.example.analytics.TradeSummaryRow` - агрегированная строка таблицы.

## Изменения в Lua

Файл:
- `C:\QuikPy\QUIK\lua\qsfunctions.lua`

Добавлена функция:
- `qsfunctions.get_trades_by_uid(msg)`

Поведение:
- `msg.data` содержит UID (число/строка с числом),
- проход по таблице `trades`,
- сравнение UID по набору полей,
- возврат массива подходящих сделок.

## База H2

Файл БД:
- `%USERPROFILE%\.quikclient\quik.mv.db`

Таблицы:
- `quik_rpc_result` - сырые RPC.
- `quik_callback` - callback-сообщения.
- `quik_trade_leg` - нормализованные сделки.

`quik_trade_leg` хранит:
- `class_code`, `sec_code`,
- `qty_lots`, `side` (`BUY`/`SELL`), `flags`,
- `unit_price`, `amount_rub`,
- `trade_num_text`, `created_at`, `rpc_result_id`.

## Логика сводки (вкладка `Сводка из H2`)

Сводка строится по **последнему batch** (`max(rpc_result_id)`):

- Актив: `sec_code` (+ `class_code`)
- Куплено лотов: `SUM(BUY.qty_lots)`
- Продано лотов: `SUM(SELL.qty_lots)`
- Остаток лотов: `buy - sell`
- Сумма покупок: `SUM(BUY.amount_rub)`
- Сумма продаж: `SUM(SELL.amount_rub)`
- Разница (реализованная):
  - `min(buy, sell) * (avg_sell - avg_buy)`
- Остаток в рублях:
  - если long: `net * avg_buy`
  - если short: `abs(net) * avg_sell`

## Направление сделки (BUY/SELL)

`TradeRowParser.isSell(...)` определяет сторону в порядке приоритета:
1. Явные поля (`operation`, `side`, `buy_sell`, `buysell`, `type`).
2. Знак `qty/quantity` (если отрицательное -> SELL).
3. Fallback: `flags & 1`.

## Команды сборки/запуска

Сборка:
- `mvn -q compile`
- `mvn -q package -DskipTests`

Артефакты:
- `target/QuikClient-1.0-SNAPSHOT.jar`
- `target/QuikClient-1.0-SNAPSHOT-all.jar` (рекомендуется для переноса)

Запуск fat-jar:
- `java -jar target/QuikClient-1.0-SNAPSHOT-all.jar`
- с аргументами портов: `java -jar ... 127.0.0.1 34132 34133`

## Важные условия для корректной работы

1. Lua-скрипт QUIK должен быть запущен в терминале QUIK.
2. Порты Java должны совпадать с настройками Lua `config.json`.
3. После изменения `qsfunctions.lua` нужно перезапустить Lua-скрипт в QUIK.
4. Для другого ПК запускать лучше из `-all.jar` (чтобы не терялись зависимости, включая H2).

## Ограничения/риски

- Имена полей UID/side в `trades` могут отличаться между брокерами/версиями QUIK.
- Логика side уже усилена, но при нестандартных полях может требовать локальной адаптации.
- Сводка сейчас по последнему batch, а не по произвольному периоду.

## Что лучше делать дальше

1. Добавить `live`-режим по callback `OnTrade`:
   - отдельная таблица `quik_trade_stream`,
   - live-обновление JTable.
2. Добавить выбор периода/UID для отчетов (не только последний batch).
3. Добавить экспорт сводки в CSV.
4. Добавить юнит-тесты на `TradeRowParser` и агрегации H2.
5. Добавить диагностику полей (debug-режим), чтобы быстро подстраиваться под поля конкретного QUIK.

## Быстрый чек-лист перед продолжением разработки

- [ ] Lua в QUIK запущен и слушает нужные порты.
- [ ] `get_trades_by_uid` присутствует в `qsfunctions.lua`.
- [ ] Java подключается (`Ping` работает).
- [ ] `Сделки по UID` возвращают данные.
- [ ] В H2 появляются записи в `quik_trade_leg`.
- [ ] Вкладка `Сводка из H2` показывает корректное разделение BUY/SELL.
