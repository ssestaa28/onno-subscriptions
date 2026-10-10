package ru.onno.subscriptions.model;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.*;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;
import java.math.BigDecimal;

@AccumulationRegister(name = "TariffRevenue", title = "Выручка по тарифам и клиентам",
        type = AccumulationType.TURNOVER, context = "Subscriptions")
@AccessControl(readRoles = "ADMIN")
@Getter @Setter
public class TariffRevenue extends AccumulationRecord {
    @Dimension(displayName = "Тариф") private Ref<Tariff> tariff;
    @Dimension(displayName = "Клиент") private Ref<Client> client;
    @Resource(displayName = "Выручка", precision = 15, scale = 2) private BigDecimal amount;
    @Resource(displayName = "Периодов", precision = 15, scale = 0) private BigDecimal periods;
}
