package ru.onno.subscriptions.model;

import su.onno.annotations.Enumeration;
import su.onno.annotations.EnumLabel;

@Enumeration(name = "SubscriptionStatuses", title = "Статусы подписок")
public enum SubscriptionStatus {
    @EnumLabel(value = "Черновик", color = "#64748B") DRAFT,
    @EnumLabel(value = "Ожидает начала", color = "#D97706") PENDING,
    @EnumLabel(value = "Активна", color = "#16A34A") ACTIVE,
    @EnumLabel(value = "Истекла", color = "#64748B") EXPIRED,
    @EnumLabel(value = "Отменена", color = "#DC2626") CANCELLED
}
