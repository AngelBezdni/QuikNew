package org.example.trade;

/**
 * Фильтр входящего потока сделок (для live OnTrade), логика AND по непустым полям.
 */
public record TradeFilterSpec(
        String classCode,
        String secCode,
        String clientCode,
        String firmId,
        String trdAccId,
        Long uid,
        Long orderNum
) {

    public static TradeFilterSpec empty() {
        return new TradeFilterSpec("", "", "", "", "", null, null);
    }

    public boolean isEmpty() {
        return blank(classCode) && blank(secCode) && blank(clientCode)
                && blank(firmId) && blank(trdAccId)
                && uid == null && orderNum == null;
    }

    public boolean matches(TradeRecord t) {
        if (!blank(classCode) && !eq(t.classCode(), classCode)) {
            return false;
        }
        if (!blank(secCode) && !eq(t.secCode(), secCode)) {
            return false;
        }
        if (!blank(clientCode) && !eq(t.clientCode(), clientCode)) {
            return false;
        }
        if (!blank(firmId) && !eq(t.firmId(), firmId)) {
            return false;
        }
        if (!blank(trdAccId) && !eq(t.trdAccId(), trdAccId)) {
            return false;
        }
        if (uid != null) {
            Long tuid = t.uid();
            if (tuid == null || !uid.equals(tuid)) {
                return false;
            }
        }
        if (orderNum != null) {
            try {
                long on = Long.parseLong(t.orderNum() == null ? "" : t.orderNum());
                if (on != orderNum) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equalsIgnoreCase(b == null ? "" : b);
    }
}
