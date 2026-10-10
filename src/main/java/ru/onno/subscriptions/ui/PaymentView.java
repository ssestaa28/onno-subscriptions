package ru.onno.subscriptions.ui;

import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.Payment;
import su.onno.ui.*;

@Component
public class PaymentView implements EntityView<Payment> {
    public Class<Payment> entity() { return Payment.class; }
    public void list(ListSpec<Payment> list) {
        list.columns(Payment::getNumber, Payment::getDate, Payment::getClient, Payment::getAmount, Payment::getMethod, Payment::isPosted)
                .label(Payment::getNumber, "Номер").label(Payment::getDate, "Дата")
                .label(Payment::getClient, "Клиент").label(Payment::getAmount, "Сумма")
                .label(Payment::getMethod, "Способ оплаты").label(Payment::isPosted, "Проведён")
                .sortBy(Payment::getDate, true);
        list.filter(Payment::getDate).label("Период").dateRange();
    }
    public void fields(EntityConfigBuilder<Payment> f) {
        f.field(Payment::getClient).order(0).width("half").hint("На чей лицевой счёт зачислить платёж");
        f.field(Payment::getDate).label("Дата").order(1).width("half").format("dd.MM.yyyy HH:mm");
        f.field(Payment::getAmount).order(2).width("half").format("currency:RUB").hint("Пополнение произойдёт после проведения");
        f.field(Payment::getMethod).order(3).width("half");
        f.field(Payment::getNumber).label("Номер").hideInForm();
        f.field(Payment::isPosted).label("Проведён").hideInForm();
    }
}
