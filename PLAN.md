# QUIK# Java-клиент: план и статус

## Уже сделано

- Подключение к Lua по двум TCP-портам (response, затем callback), проверка `ping` (`LuaConnectionTest`).
- Запрос `get_trades` с увеличенными таймаутами, повторами и **потоковым чтением** одной JSON-строки до `\n` (`LuaTradesRequest`) — большие ответы не режутся `readLine` так же критично.

## Текущая реализация (этот шаг)

1. **Общий слой** `org.example.quik`: настройки подключения, открытие пары сокетов, отправка JSON+`\n`, чтение одной строки ответа потоково.
2. **H2** (`H2TradeStore`): файл `./data/quik_trades` (переживает перезапуск), таблица сделок, дедуп по ключу `(class_code, sec_code, trade_num)` с нормализацией и запасным ключом от `raw_json`.
3. **Bootstrap** (`TradeBootstrap`): после открытия пары сокетов — `get_trades`, разбор `data` как массива сделок, запись в H2.
4. **Слушатель** (`TradeCallbackListener`): отдельный поток читает callback-сокет построчно; при `cmd == "OnTrade"` пишет сделку в H2 с тем же дедупом.
5. **Точка входа** (`TradeSyncApp` / `Main`): bootstrap → повторное открытие пары → запуск слушателя; выход по Enter (закрытие сокетов).

## Фильтры таблицы «Сделки» в QUIK

Задаются только в терминале QUIK; Java не дублирует их — в `get_trades` и `OnTrade` приходят уже отфильтрованные данные.

## Позже (не в этом PR)

- Сводная таблица куплено/продано по H2 (агрегации по инструменту/дню).
- Вынести `LuaTradesRequest` полностью на общий `QuikLineProtocol` (сейчас протокол продублирован минимально в старом классе — по желанию унифицировать).

## Запуск

```text
mvn -q compile exec:java -Dexec.mainClass=org.example.Main
```

Аргументы (как у `LuaTradesRequest`): `host responsePort callbackPort readTimeoutMs connectTimeoutMs attempts`

Проверка только соединения:

```text
mvn -q compile exec:java -Dexec.mainClass=org.example.LuaConnectionTest
```

Переопределение файла БД (опционально): системное свойство `quik.h2.file`, например `-Dquik.h2.file=C:/data/my_trades`.

Очень большой JSON одной строкой (`get_trades`): по умолчанию допускается до **200 млн** символов на строку; иначе задайте `-Dquik.max.json.chars=300000000` и достаточный **`-Xmx`** (ответ целиком собирается в памяти).
