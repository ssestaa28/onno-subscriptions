package ru.onno.subscriptions.model;

import su.onno.annotations.Enumeration;
import su.onno.annotations.EnumLabel;

@Enumeration(name = "ClientStatuses", title = "Статусы клиентов")
public enum ClientStatus {
    @EnumLabel(value = "Активен", color = "#16A34A") ACTIVE,
    @EnumLabel(value = "Заблокирован", color = "#DC2626") BLOCKED
}
