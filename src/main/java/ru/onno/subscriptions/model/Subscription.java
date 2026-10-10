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
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Document(name = "Subscriptions", title = "Подписки", numberPrefix = "SUB-", context = "Subscriptions")
@AccessControl(readRoles = "ADMIN", writeRoles = "ADMIN")
@Getter @Setter
public class Subscription extends DocumentObject
        implements OnFillingHandler, BeforeWriteHandler, Validated, Postable {
    @Attribute(displayName = "Клиент", required = true) private Ref<Client> client;
    @Attribute(displayName = "Статус", required = true)
    private SubscriptionStatus status = SubscriptionStatus.DRAFT;
    @Attribute(displayName = "Дата начала", required = true)
    private LocalDate startsAt = LocalDate.now(ZoneId.of("Europe/Moscow"));
    @Attribute(displayName = "Дата окончания") private LocalDate endsAt;
    @Attribute(displayName = "Итого", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Attribute(displayName = "Причина отмены", length = 1000) private String cancellationReason;
    @Attribute(displayName = "Учтена в выручке") private Boolean recognized = false;
    @TabularSection(name = "lines") private List<SubscriptionLine> lines = new ArrayList<>();
    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Setter(lombok.AccessLevel.NONE)
    @Getter(lombok.AccessLevel.NONE)
    private boolean cancellationRequested;

    public void cancel(String reason) {
        cancellationRequested = true;
        status = SubscriptionStatus.CANCELLED;
        cancellationReason = reason;
    }

    public Subscription() { setDate(LocalDateTime.now(ZoneId.of("Europe/Moscow")).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)); }

    @Override public void onFilling() {
        if (getDate() == null) setDate(DomainServices.now());
        if (startsAt == null) startsAt = DomainServices.today();
        if (status == null) status = SubscriptionStatus.DRAFT;
    }

    @Override public void beforeWrite() {
        protectPostedTerms();
        total = BigDecimal.ZERO.setScale(2);
        long longestDays = 0;
        if (lines != null) for (var line : lines) {
            if (line == null) continue;
            // Drafts use current tariff terms. Posted documents retain the purchased terms,
            // including when the scheduled task saves only their lifecycle status.
            if (!isPosted()) {
                Tariff tariff = DomainServices.active(line.getTariff());
                line.setPrice(tariff == null ? null : tariff.getPrice());
                line.setPeriodDays(tariff == null ? null : tariff.getPeriodDays());
            }
            int periods = line.getPeriods() == null ? 0 : line.getPeriods();
            BigDecimal price = line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
            line.setAmount(price.multiply(BigDecimal.valueOf(periods)).setScale(2, RoundingMode.HALF_UP));
            total = total.add(line.getAmount());
            if (line.getPeriodDays() != null && periods > 0) {
                longestDays = Math.max(longestDays, (long) line.getPeriodDays() * periods);
            }
        }
        endsAt = startsAt == null ? null : startsAt.plusDays(longestDays);
        recognized = isPosted() && status != SubscriptionStatus.CANCELLED;
    }

    private void protectPostedTerms() {
        if (getId() == null) return;
        var stored = DomainServices.bean(ru.onno.subscriptions.repository.SubscriptionRepository.class)
                .findActiveById(getId()).orElse(null);
        if (stored == null || !stored.isPosted()) return;
        boolean same = Objects.equals(client, stored.getClient())
                && Objects.equals(startsAt, stored.getStartsAt())
                && Objects.equals(getDate(), stored.getDate())
                && lines != null && stored.getLines() != null && lines.size() == stored.getLines().size();
        if (same) for (int i = 0; i < lines.size(); i++) {
            var a = lines.get(i);
            var b = stored.getLines().get(i);
            if (a == null || !Objects.equals(a.getTariff(), b.getTariff())
                    || !Objects.equals(a.getPeriods(), b.getPeriods())
                    || !equalMoney(a.getPrice(), b.getPrice())
                    || !Objects.equals(a.getPeriodDays(), b.getPeriodDays())) { same = false; break; }
        }
        if (!same) throw new su.onno.validation.ValidationException("Перед изменением условий снимите проведение подписки");
        boolean wasCancelled = stored.getStatus() == SubscriptionStatus.CANCELLED;
        if (wasCancelled && status != SubscriptionStatus.CANCELLED)
            throw new su.onno.validation.ValidationException("Отменённую подписку нельзя возобновить; создайте новую");
        if (!wasCancelled && status == SubscriptionStatus.CANCELLED && !cancellationRequested)
            throw new su.onno.validation.ValidationException("Используйте действие «Отменить подписку», чтобы вернуть деньги");
    }

    private static boolean equalMoney(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    @Override public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("client", "Выберите существующего клиента",
                        () -> DomainServices.active(client) != null),
                BusinessRule.onField("startsAt", "Укажите дату начала", () -> startsAt != null),
                new BusinessRule("lines-required", "Добавьте хотя бы одну строку",
                        () -> lines != null && !lines.isEmpty() && lines.stream().allMatch(Objects::nonNull)),
                new BusinessRule("positive-periods", "Число периодов в каждой строке должно быть больше нуля",
                        () -> lines != null && lines.stream().allMatch(l -> l != null && l.getPeriods() != null && l.getPeriods() > 0)),
                new BusinessRule("available-tariffs", "Все тарифы должны существовать и быть доступны для подключения",
                        () -> isPosted() || lines != null && lines.stream().allMatch(l -> {
                            if (l == null) return false;
                            Tariff tariff = DomainServices.active(l.getTariff());
                            return tariff != null && Boolean.TRUE.equals(tariff.getAvailable())
                                    && tariff.getPrice() != null && tariff.getPrice().signum() >= 0
                                    && tariff.getPeriodDays() != null && tariff.getPeriodDays() > 0;
                        })));
    }

    /** End date is exclusive: [startsAt, endsAt). */
    public SubscriptionStatus statusOn(LocalDate date) {
        if (status == SubscriptionStatus.CANCELLED) return status;
        if (!isPosted()) return SubscriptionStatus.DRAFT;
        if (!date.isBefore(endsAt)) return SubscriptionStatus.EXPIRED;
        return date.isBefore(startsAt) ? SubscriptionStatus.PENDING : SubscriptionStatus.ACTIVE;
    }

    @Override public void handlePosting(PostingContext context) {
        if (status == SubscriptionStatus.CANCELLED) {
            recognized = false;
            return;
        }
        var accounts = context.movements(PersonalAccount.class);
        accounts.addExpense(m -> { m.setClient(client); m.setAmount(total); });
        var revenue = context.movements(TariffRevenue.class);
        for (var line : lines) revenue.addReceipt(m -> {
            m.setClient(client);
            m.setTariff(line.getTariff());
            m.setAmount(line.getAmount());
            m.setPeriods(BigDecimal.valueOf(line.getPeriods()));
        });
        LocalDate today = DomainServices.today();
        status = !today.isBefore(endsAt) ? SubscriptionStatus.EXPIRED
                : today.isBefore(startsAt) ? SubscriptionStatus.PENDING : SubscriptionStatus.ACTIVE;
        recognized = true;
    }
}
