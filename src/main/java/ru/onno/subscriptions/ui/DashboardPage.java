package ru.onno.subscriptions.ui;
import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.*;
import su.onno.ui.*;
@Component
public class DashboardPage implements Page {
    public String route() { return "/"; }
    public void compose(PageBuilder b) {
        b.title("Сервис подписок");
        b.subtitle("Пополнения, подписки и выручка за выбранный период");
        b.widget("Период").type("timeRange").width("full").order(0)
                .config("presets", "7d,30d,90d,1y,all").config("default", "30d");
        b.widget("Клиенты").type("count").catalog(Client.class).width("1/3").order(1);
        b.widget("Пополнения").type("stat").document(Payment.class).dateField(Payment::getDate)
                .metricField(Payment::getAmount).config("metric", "sum").config("currency", "RUB")
                .config("filter", "_posted = true").width("1/3").order(2);
        b.widget("Выручка").type("stat").document(Subscription.class).dateField(Subscription::getDate)
                .metricField(Subscription::getTotal).config("metric", "sum").config("currency", "RUB")
                .config("filter", "_posted = true AND recognized = true").width("1/3").order(3);
        b.widget("Выручка по дням").type("chart").document(Subscription.class)
                .dateField(Subscription::getDate).groupBy(Subscription::getDate).metricField(Subscription::getTotal)
                .config("kind", "area").config("groupByDate", "day").config("metric", "sum")
                .config("filter", "_posted = true AND recognized = true").width("full").order(4);
        b.widget("Последние подписки").type("list").document(Subscription.class).width("full").order(5)
                .maxItems(10).config("titleTemplate", "{number} · {clientDisplay}")
                .config("secondaryField", "statusDisplay");
    }
}
