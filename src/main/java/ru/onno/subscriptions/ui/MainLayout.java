package ru.onno.subscriptions.ui;
import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.*;
import su.onno.ui.*;
@Component
public class MainLayout implements Layout {
    public void configure(LayoutSpec layout) {
        layout.shell().brand("Сервис подписок").nav(NavStyle.SIDEBAR);
        layout.section("Обзор").order(0).icon("chart-column").page("/", "Дашборд", "chart-column");
        layout.section("Справочники").order(1).icon("users").catalog(Client.class).catalog(Tariff.class);
        layout.section("Документы").order(2).icon("file-text").document(Payment.class).document(Subscription.class);
        layout.section("Отчёты").order(3).icon("wallet").register(PersonalAccount.class).register(TariffRevenue.class);
    }
}
