package ru.onno.subscriptions.model;

import su.onno.annotations.Enumeration;
import su.onno.annotations.EnumLabel;

@Enumeration(name = "PaymentMethods", title = "Способы оплаты")
public enum PaymentMethod {
    @EnumLabel("Банковская карта") CARD,
    @EnumLabel("Банковский перевод") BANK_TRANSFER,
    @EnumLabel("Наличные") CASH
}
