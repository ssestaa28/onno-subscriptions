package ru.onno.subscriptions.ui;
import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.TariffRevenue;
import su.onno.ui.*;
@Component
public class TariffRevenueView implements EntityView<TariffRevenue> {
    public Class<TariffRevenue> entity() { return TariffRevenue.class; }
    public void fields(EntityConfigBuilder<TariffRevenue> f) {
        f.field(TariffRevenue::getTariff).order(0).label("Тариф");
        f.field(TariffRevenue::getClient).order(1).label("Клиент");
        f.field(TariffRevenue::getAmount).order(2).format("currency:RUB").hint("Выручка по проведённым подпискам");
        f.field(TariffRevenue::getPeriods).order(3).format("integer");
        f.field(TariffRevenue::getPeriod).format("dd.MM.yyyy HH:mm");
    }
}
