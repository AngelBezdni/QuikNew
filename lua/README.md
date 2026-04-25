# Lua scripts for deployment

Эта папка содержит Lua-скрипты для переноса на рабочую машину с QUIK.

## Что здесь

- `qsfunctions.get_trades_by_uid.lua` - функция `get_trades_by_uid(msg)` для `qsfunctions.lua`.
- `qsfunctions.full.lua` - полный файл `qsfunctions.lua` (уже с `get_trades_by_uid`), можно копировать целиком.

## Как установить на рабочей машине

1. Откройте файл `qsfunctions.lua` в каталоге Lua вашего QUIK/QuikSharp.
2. Вариант A: замените `qsfunctions.lua` целиком файлом `qsfunctions.full.lua`.
3. Вариант B: скопируйте только содержимое `qsfunctions.get_trades_by_uid.lua` в текущий `qsfunctions.lua` (после функции `get_trades`).
4. Сохраните файл.
5. Перезапустите Lua-скрипт в QUIK (или перезапустите QUIK).

## Проверка

Java-клиент вызывает команду:

- `cmd = "get_trades_by_uid"`
- `data = "<uid>"`

Если функция установлена корректно, сервер вернет массив сделок только по заданному UID.
