# Быстрый запуск фильтра сделок

1. Заполните файл `lua/filter.by_client_uid.json` своими значениями:
   - `client_code`
   - `uid`
   - при необходимости `class_code`, `sec_code`

2. Убедитесь, что в логе запуска видно:
   - `Bootstrap mode: mode=get_trades_filtered, source=filter_file(...)`
   - `Bootstrap RPC: {"cmd":"get_trades_filtered", ...}`

3. Пример запуска через Maven:

```text
mvn -q compile exec:java -Dexec.mainClass=org.example.Main -Dexec.jvmArgs="-Dquik.get_trades.filter_file=C:/Java/QuikClient/QuikClient/lua/filter.by_client_uid.json -Xmx4g"
```

4. Если нужен только ping-проверка:

```text
mvn -q compile exec:java -Dexec.mainClass=org.example.Main -Dexec.args="ping 127.0.0.1 34130 34131"
```

5. Важно: обновлённые `qsfunctions.lua` и `qsutils.lua` должны быть скопированы в реальную папку скриптов QUIK, откуда запускается `QuikSharp.lua`.
