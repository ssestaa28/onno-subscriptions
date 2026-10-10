package ru.onno.subscriptions.model;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.*;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;
import java.math.BigDecimal;

// onno checks negative balances inside the posting transaction, including concurrent posts.
@AccumulationRegister(name = "PersonalAccounts", title = "Остатки лицевых счетов",
        type = AccumulationType.BALANCE, allowNegative = false, context = "Subscriptions")
@AccessControl(readRoles = "ADMIN")
@Getter @Setter
public class PersonalAccount extends AccumulationRecord {
    @Dimension(displayName = "Клиент")
    private Ref<Client> client;
    @Resource(displayName = "Остаток", precision = 15, scale = 2)
    private BigDecimal amount;
}
