package ru.onno.subscriptions.ui;

import org.springframework.stereotype.Component;
import ru.onno.subscriptions.model.Client;
import su.onno.ui.*;

@Component
public class ClientView implements EntityView<Client> {
    public Class<Client> entity() { return Client.class; }
    public void list(ListSpec<Client> list) {
        list.columns(Client::getDescription, Client::getStatus, Client::getEmail, Client::getPhone, Client::getRegisteredAt)
                .label(Client::getDescription, "Клиент").label(Client::getStatus, "Статус")
                .label(Client::getEmail, "E-mail").label(Client::getPhone, "Телефон")
                .label(Client::getRegisteredAt, "Регистрация").sortBy(Client::getDescription, false);
        list.filter(Client::getEmail).label("E-mail").contains();
    }
    public void fields(EntityConfigBuilder<Client> f) {
        f.field(Client::getDescription).label("Имя клиента").order(0).width("half").hint("Имя или название компании");
        f.field(Client::getStatus).order(1).width("half").hint("Текущий статус клиента");
        f.field(Client::getEmail).order(2).width("half").placeholder("client@example.com");
        f.field(Client::getPhone).order(3).width("half").placeholder("+7 999 123-45-67");
        f.field(Client::getRegisteredAt).order(4).width("half").format("dd.MM.yyyy").hint("Подставляется при создании");
        f.field(Client::getCode).label("Код").hideInForm();
    }
}
