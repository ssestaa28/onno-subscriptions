package ru.onno.subscriptions.model;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.*;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import java.time.LocalDate;
import java.util.List;

@Catalog(name = "Clients", title = "Клиенты", codePrefix = "CL-", context = "Subscriptions")
@AccessControl(readRoles = "ADMIN", writeRoles = "ADMIN")
@Getter @Setter
public class Client extends CatalogObject implements Validated {
    @Attribute(displayName = "Статус", required = true)
    private ClientStatus status = ClientStatus.ACTIVE;
    @Attribute(displayName = "E-mail", email = true)
    private String email;
    @Attribute(displayName = "Телефон", length = 30)
    private String phone;
    @Attribute(displayName = "Дата регистрации", required = true)
    private LocalDate registeredAt = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));

    @Override public List<BusinessRule> rules() {
        return List.of(new BusinessRule("client-name", "Укажите имя клиента",
                () -> getDescription() != null && !getDescription().isBlank()));
    }
}
