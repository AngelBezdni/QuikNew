-- Добавить в файл qsfunctions.lua (например после функции get_trades)
-- Команда для QuikSharp: получение сделок по UID с фильтрацией на стороне Lua
function qsfunctions.get_trades_by_uid(msg)
	local uid = tonumber(msg.data)
	if uid == nil then
		msg.cmd = "lua_error"
		msg.lua_error = "get_trades_by_uid: uid должен быть числом"
		return msg
	end

	local function read_trade_field(trade, key)
		local ok, v = pcall(function() return trade[key] end)
		if ok then
			return v
		end
		return nil
	end

	local function trade_uid(trade)
		local keys = {
			"on_behalf_of_uid",
			"userid",
			"user_id",
			"uid",
			"client_uid",
			"user",
			"userid_ext",
			"investment_decision_maker_short_code",
			"executing_trader_short_code",
			"client_short_code",
		}
		for _, key in ipairs(keys) do
			local v = read_trade_field(trade, key)
			if v ~= nil and v ~= "" then
				local n = tonumber(tostring(v))
				if n ~= nil and n == uid then
					return true
				end
			end
		end
		return false
	end

	local trades = {}
	for i = 0, getNumberOf("trades") - 1 do
		local trade = getItem("trades", i)
		if trade_uid(trade) then
			table.insert(trades, trade)
		end
	end
	msg.data = trades
	return msg
end
