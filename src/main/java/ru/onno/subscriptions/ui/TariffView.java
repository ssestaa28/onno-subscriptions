package ru.onno.subscriptions.ui;

import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.Tariff;
import su.onno.ui.*;

@Component
public class TariffView implements EntityView<Tariff> {
    public Class<Tariff> entity() { return Tariff.class; }
    public void list(ListSpec<Tariff> list) {
        list.columns(Tariff::getDescription, Tariff::getPrice, Tariff::getPeriodDays, Tariff::getAvailable)
                .label(Tariff::getDescription, "Тариф").label(Tariff::getPrice, "Цена за период")
                .label(Tariff::getPeriodDays, "Дней в периоде").label(Tariff::getAvailable, "Доступен")
                .sortBy(Tariff::getDescription, false);
    }
    public void fields(EntityConfigBuilder<Tariff> f) {
        f.field(Tariff::getDescription).label("Название тарифа").order(0).width("full").hint("Например, доступ на месяц");
        f.field(Tariff::getPrice).order(1).width("half").format("currency:RUB").hint("Стоимость одного периода в рублях");
        f.field(Tariff::getPeriodDays).order(2).width("half").hint("Положительное число календарных дней");
        f.field(Tariff::getAvailable).order(3).width("half").widget("switch").hint("Выключите, чтобы запретить новые подключения");
        f.field(Tariff::getCode).label("Код").hideInForm();
    }
}
