package ru.onno.subscriptions.model;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.*;
import su.onno.model.DocumentObject;
import su.onno.lifecycle.*;
import su.onno.rules.*;
import su.onno.posting.PostingContext;
import su.onno.types.Ref;
import ru.onno.subscriptions.service.DomainServices;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Document(name = "Payments", title = "Платежи", numberPrefix = "PAY-", context = "Subscriptions")
@AccessControl(readRoles = "ADMIN", writeRoles = "ADMIN")
@Getter @Setter
public class Payment extends DocumentObject implements OnFillingHandler, BeforeWriteHandler, Validated, Postable {
    @Attribute(displayName = "Клиент", required = true) private Ref<Client> client;
    @Attribute(displayName = "Сумма", required = true, precision = 15, scale = 2)
    private BigDecimal amount;
    @Attribute(displayName = "Способ оплаты", required = true)
    private PaymentMethod method = PaymentMethod.CARD;

    public Payment() { setDate(LocalDateTime.now(ZoneId.of("Europe/Moscow")).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)); }
    @Override public void onFilling() {
        if (getDate() == null) setDate(DomainServices.now());
    }
    @Override public void beforeWrite() {
        if (getId() == null) return;
        var stored = DomainServices.bean(ru.onno.subscriptions.repository.PaymentRepository.class)
                .findActiveById(getId()).orElse(null);
        if (stored != null && stored.isPosted() &&
                (!java.util.Objects.equals(client, stored.getClient())
                || !java.util.Objects.equals(getDate(), stored.getDate())
                || amount == null || stored.getAmount() == null || amount.compareTo(stored.getAmount()) != 0)) {
            throw new su.onno.validation.ValidationException("Перед изменением суммы, клиента или даты снимите проведение платежа");
        }
    }
    @Override public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("client", "Выберите существующего клиента",
                        () -> DomainServices.active(client) != null),
                BusinessRule.onField("amount", "Сумма платежа должна быть больше нуля",
                        () -> amount != null && amount.signum() > 0),
                BusinessRule.onField("method", "Выберите способ оплаты", () -> method != null));
    }
    @Override public void handlePosting(PostingContext context) {
        context.movements(PersonalAccount.class).addReceipt(m -> {
            m.setClient(client);
            m.setAmount(amount);
        });
    }
}
