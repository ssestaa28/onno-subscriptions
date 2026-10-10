package ru.onno.subscriptions.ui;
import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.PersonalAccount;
import su.onno.ui.*;
@Component
public class PersonalAccountView implements EntityView<PersonalAccount> {
    public Class<PersonalAccount> entity() { return PersonalAccount.class; }
    public void fields(EntityConfigBuilder<PersonalAccount> f) {
        f.field(PersonalAccount::getClient).order(0).label("Клиент").width("half");
        f.field(PersonalAccount::getAmount).order(1).format("currency:RUB").hint("Пополнения минус стоимость проведённых подписок");
        f.field(PersonalAccount::getPeriod).format("dd.MM.yyyy HH:mm");
    }
}
