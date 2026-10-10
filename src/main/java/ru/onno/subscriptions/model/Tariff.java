package ru.onno.subscriptions.model;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.*;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import java.math.BigDecimal;
import java.util.List;

@Catalog(name = "Tariffs", title = "Тарифы", codePrefix = "TR-", context = "Subscriptions")
@AccessControl(readRoles = "ADMIN", writeRoles = "ADMIN")
@Getter @Setter
public class Tariff extends CatalogObject implements Validated {
    @Attribute(displayName = "Цена за период", required = true, precision = 15, scale = 2, min = 0)
    private BigDecimal price = BigDecimal.ZERO;
    @Attribute(displayName = "Длительность периода, дней", required = true, min = 1)
    private Integer periodDays = 30;
    @Attribute(displayName = "Доступен для подключения", required = true)
    private Boolean available = true;

    @Override public List<BusinessRule> rules() {
        return List.of(
                new BusinessRule("tariff-name", "Укажите название тарифа",
                        () -> getDescription() != null && !getDescription().isBlank()),
                BusinessRule.onField("price", "Цена должна быть неотрицательной",
                        () -> price != null && price.signum() >= 0),
                BusinessRule.onField("periodDays", "Длительность должна быть больше нуля",
                        () -> periodDays != null && periodDays > 0));
    }
}
