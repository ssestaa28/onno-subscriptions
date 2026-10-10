package ru.onno.subscriptions.ui;

import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.*;
import ru.onno.subscriptions.service.SubscriptionCancellation;
import su.onno.ui.*;

@Component
public class SubscriptionView implements EntityView<Subscription> {
    private final SubscriptionCancellation cancellation;
    public SubscriptionView(SubscriptionCancellation cancellation) { this.cancellation = cancellation; }
    public Class<Subscription> entity() { return Subscription.class; }
    public void list(ListSpec<Subscription> list) {
        list.columns(Subscription::getNumber, Subscription::getClient, Subscription::getStatus,
                        Subscription::getStartsAt, Subscription::getEndsAt, Subscription::getTotal, Subscription::isPosted)
                .label(Subscription::getNumber, "Номер").label(Subscription::getClient, "Клиент")
                .label(Subscription::getStatus, "Статус").label(Subscription::getStartsAt, "Начало")
                .label(Subscription::getEndsAt, "Окончание").label(Subscription::getTotal, "Итого")
                .label(Subscription::isPosted, "Проведена").sortBy(Subscription::getDate, true);
        list.filter(Subscription::getDate).label("Период оформления").dateRange();
    }
    public void fields(EntityConfigBuilder<Subscription> f) {
        f.field(Subscription::getClient).order(0).width("half").hint("Стоимость будет списана с лицевого счёта клиента");
        f.field(Subscription::getDate).label("Дата документа").order(1).width("half").format("dd.MM.yyyy HH:mm");
        f.field(Subscription::getStartsAt).order(2).width("half").format("dd.MM.yyyy").hint("Первый день действия подписки");
        f.field(Subscription::getStatus).order(3).width("half").hint("Для новой подписки оставьте «Черновик». При проведении статус определяется автоматически.");
        f.field(Subscription::getEndsAt).order(4).width("half").format("dd.MM.yyyy").hideInForm()
                .hint("Не включается в срок действия; рассчитывается по самой длинной строке");
        f.field(Subscription::getTotal).order(5).width("half").format("currency:RUB").hideInForm().hint("Сумма всех строк");
        f.field(Subscription::getLines).label("Тарифы").order(6).width("full");
        f.rowField(Subscription::getLines, SubscriptionLine::getTariff).order(0).hint("Выберите доступный тариф").refFilter("available = true");
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriods).order(1).hint("Положительное целое число");
        f.rowField(Subscription::getLines, SubscriptionLine::getPrice).order(2).format("currency:RUB").hideInForm().hint("Подставляется из тарифа");
        f.rowField(Subscription::getLines, SubscriptionLine::getAmount).order(3).format("currency:RUB").hideInForm().hint("Цена × число периодов");
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriodDays).hideInForm().hideInDetail().hideInList();
        f.field(Subscription::getCancellationReason).order(7).width("full").widget("textarea").hideInForm();
        f.field(Subscription::getRecognized).hideInForm().hideInDetail().hideInList();
        f.field(Subscription::getNumber).label("Номер").hideInForm();
        f.field(Subscription::isPosted).label("Проведена").hideInForm();
    }
    public void actions(ActionSpec a) {
        for (var scope : new ActionScope[]{ActionScope.ROW, ActionScope.DETAIL}) {
            a.action(scope == ActionScope.ROW ? "cancel" : "cancel-detail")
                    .scope(scope).label("Отменить подписку").icon("ban")
                    .visibleWhen(row -> row.enumValue(Subscription::getStatus, SubscriptionStatus.class) != SubscriptionStatus.CANCELLED)
                    .form(f -> f.input("reason").label("Причина отмены").type(InputType.TEXTAREA)
                            .placeholder("Почему отменяем подписку?").required())
                    .handler(ctx -> {
                        cancellation.cancel(ctx.id(), ctx.input("reason"));
                        return ActionResult.refresh(ActionToast.success("Подписка отменена, деньги возвращены"));
                    });
        }
    }
}
