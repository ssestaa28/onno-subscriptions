package ru.onno.subscriptions;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.onno.subscriptions.model.*;
import ru.onno.subscriptions.repository.*;
import ru.onno.subscriptions.service.*;
import su.onno.posting.PostingService;
import su.onno.types.Ref;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real onno lifecycle, generated schema, JDBC repositories and posting transactions on H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SubscriptionIntegrationTest {
    @Autowired ClientRepository clients;
    @Autowired TariffRepository tariffs;
    @Autowired PaymentRepository payments;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PersonalAccountRepository accounts;
    @Autowired TariffRevenueRepository revenue;
    @Autowired PostingService posting;
    @Autowired SubscriptionCancellation cancellation;
    private Client client;
    private Tariff monthly;

    @BeforeEach void setup() {
        client = new Client();
        client.setDescription("Клиент " + UUID.randomUUID());
        client.setEmail("test@example.com");
        client = clients.save(client);
        monthly = tariff("Месяц", "100.00", 30);
    }

    private Tariff tariff(String name, String price, int days) {
        var tariff = new Tariff();
        tariff.setDescription(name);
        tariff.setPrice(new BigDecimal(price));
        tariff.setPeriodDays(days);
        return tariffs.save(tariff);
    }
    private Ref<Client> clientRef() { return Ref.of(Client.class, client.getId()); }
    private SubscriptionLine line(Tariff tariff, int periods) {
        var line = new SubscriptionLine();
        line.setTariff(Ref.of(Tariff.class, tariff.getId()));
        line.setPeriods(periods);
        return line;
    }
    private Subscription subscription(SubscriptionLine... lines) {
        var s = new Subscription();
        s.setClient(clientRef());
        s.setStartsAt(LocalDate.of(2026, 10, 1));
        s.setLines(new ArrayList<>(Arrays.asList(lines)));
        return s;
    }
    private Payment pay(String amount) {
        var p = new Payment();
        p.setClient(clientRef());
        p.setAmount(new BigDecimal(amount));
        p = payments.save(p);
        posting.post(p);
        return p;
    }
    private BigDecimal balance() {
        return accounts.getBalance(Map.of("client", clientRef())).stream()
                .map(PersonalAccount::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal revenueForClient() {
        return revenue.getTurnover(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(2100, 1, 1, 0, 0),
                        Map.of("client", clientRef())).stream().map(TariffRevenue::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test void paymentCreditsAccountOnlyWhenPosted() {
        var p = new Payment(); p.setClient(clientRef()); p.setAmount(new BigDecimal("500.00"));
        p = payments.save(p);
        assertThat(balance()).isZero();
        posting.post(p);
        assertThat(balance()).isEqualByComparingTo("500.00");
    }
    @Test void computesPriceAmountsAndLongestDurationOnEverySave() {
        var annual = tariff("Год", "900.00", 365);
        var s = subscriptions.save(subscription(line(monthly, 2), line(annual, 1)));
        assertThat(s.getTotal()).isEqualByComparingTo("1100.00");
        assertThat(s.getEndsAt()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(s.getLines().getFirst().getPrice()).isEqualByComparingTo("100.00");
        s.getLines().getFirst().setPeriods(13);
        s = subscriptions.save(s);
        assertThat(s.getTotal()).isEqualByComparingTo("2200.00");
        assertThat(s.getEndsAt()).isEqualTo(s.getStartsAt().plusDays(390));
    }
    @Test void postingDebitsAccountAndRecognizesRevenueByTariffAndClient() {
        pay("1000.00");
        var annual = tariff("Год", "300.00", 365);
        var s = subscriptions.save(subscription(line(monthly, 2), line(annual, 1)));
        posting.post(s);
        assertThat(balance()).isEqualByComparingTo("500.00");
        assertThat(revenueForClient()).isEqualByComparingTo("500.00");
        var rows = revenue.getRecordsByDocument(s.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(row -> row.getClient().equals(clientRef()));
        assertThat(rows).extracting(TariffRevenue::getTariff)
                .containsExactlyInAnyOrder(Ref.of(Tariff.class, monthly.getId()), Ref.of(Tariff.class, annual.getId()));
        assertThat(rows).filteredOn(r -> r.getTariff().id().equals(monthly.getId()))
                .singleElement().satisfies(r -> assertThat(r.getPeriods()).isEqualByComparingTo("2"));
    }
    @Test void insufficientFundsRollBackBothRegistersAndDocument() {
        pay("99.99");
        var s = subscriptions.save(subscription(line(monthly, 1)));
        assertThatThrownBy(() -> posting.post(s)).isInstanceOf(RuntimeException.class);
        assertThat(balance()).isEqualByComparingTo("99.99");
        assertThat(revenueForClient()).isZero();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().isPosted()).isFalse();
        assertThat(accounts.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(revenue.getRecordsByDocument(s.getId())).isEmpty();
    }
    @Test void exactBalanceCanBeSpentAndRepeatedPostCannotChargeTwice() {
        pay("100.00");
        var s = subscriptions.save(subscription(line(monthly, 1)));
        posting.post(s);
        assertThat(balance()).isZero();
        assertThatThrownBy(() -> posting.post(s)).isInstanceOf(RuntimeException.class);
        assertThat(revenueForClient()).isEqualByComparingTo("100.00");
    }
    @Test void cancelledSubscriptionCreatesNoMovementsEvenWithoutFunds() {
        var s = subscription(line(monthly, 1));
        s.setStatus(SubscriptionStatus.CANCELLED);
        s = subscriptions.save(s);
        posting.post(s);
        assertThat(balance()).isZero();
        assertThat(revenueForClient()).isZero();
        assertThat(accounts.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(revenue.getRecordsByDocument(s.getId())).isEmpty();
    }
    @Test void cancellingPostedSubscriptionReturnsFundsAndRemovesRevenueIdempotently() {
        pay("500.00");
        var s = subscriptions.save(subscription(line(monthly, 2)));
        posting.post(s);
        cancellation.cancel(s.getId(), "По просьбе клиента");
        cancellation.cancel(s.getId(), "Повторный запрос");
        assertThat(balance()).isEqualByComparingTo("500.00");
        assertThat(revenueForClient()).isZero();
        var cancelled = subscriptions.findActiveById(s.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("По просьбе клиента");
        assertThat(cancelled.getRecognized()).isFalse();
        assertThat(accounts.getRecordsByDocument(s.getId())).noneMatch(PersonalAccount::isActive);
        assertThat(revenue.getRecordsByDocument(s.getId())).noneMatch(TariffRevenue::isActive);
    }
    @Test void customerIsRequired() {
        var s = subscription(line(monthly, 1)); s.setClient(null);
        assertThatThrownBy(() -> subscriptions.save(s)).isInstanceOf(RuntimeException.class);
    }
    @Test void atLeastOneLineIsRequiredIncludingForCancelledDocuments() {
        var s = subscription();
        assertThatThrownBy(() -> subscriptions.save(s)).isInstanceOf(RuntimeException.class);
        s.setStatus(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> subscriptions.save(s)).isInstanceOf(RuntimeException.class);
    }
    @Test void zeroAndNegativePeriodsAreRejected() {
        for (int count : new int[]{0, -1}) {
            var s = subscription(line(monthly, count));
            assertThatThrownBy(() -> subscriptions.save(s)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void unavailableTariffIsRejectedOnSaveAndOnPost() {
        var s = subscriptions.save(subscription(line(monthly, 1)));
        monthly.setAvailable(false); tariffs.save(monthly);
        assertThatThrownBy(() -> subscriptions.save(subscription(line(monthly, 1)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> posting.post(s)).isInstanceOf(RuntimeException.class);
    }
    @Test void deletedTariffAndDeletedClientCannotBeUsed() {
        tariffs.delete(monthly);
        assertThatThrownBy(() -> subscriptions.save(subscription(line(monthly, 1)))).isInstanceOf(RuntimeException.class);
        var available = tariff("Другой", "100.00", 30);
        clients.delete(client);
        assertThatThrownBy(() -> subscriptions.save(subscription(line(available, 1)))).isInstanceOf(RuntimeException.class);
    }
    @Test void negativeAndZeroPaymentsAreRejected() {
        for (String amount : new String[]{"0", "-100"}) {
            var p = new Payment(); p.setClient(clientRef()); p.setAmount(new BigDecimal(amount));
            assertThatThrownBy(() -> payments.save(p)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void scheduledJobUsesInclusiveStartAndExclusiveEndAndLeavesDraftsAndCancellations() {
        pay("500.00");
        var s = subscriptions.save(subscription(line(monthly, 1)));
        posting.post(s);
        var clock = Clock.fixed(Instant.parse("2026-09-30T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        new SubscriptionStatusJob(subscriptions, clock).execute();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        clock = Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        new SubscriptionStatusJob(subscriptions, clock).execute();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        clock = Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), ZoneId.of("Europe/Moscow"));
        new SubscriptionStatusJob(subscriptions, clock).execute();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(balance()).isEqualByComparingTo("400");
        assertThat(revenueForClient()).isEqualByComparingTo("100");
        var draft = subscriptions.save(subscription(line(monthly, 1)));
        cancellation.cancel(s.getId(), "Отмена");
        new SubscriptionStatusJob(subscriptions, clock).execute();
        assertThat(subscriptions.findActiveById(draft.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.DRAFT);
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }
    @Test void postedTermsSurviveTariffChangesAndBackgroundStatusUpdate() {
        pay("500.00");
        var s = subscriptions.save(subscription(line(monthly, 2)));
        posting.post(s);
        monthly.setPrice(new BigDecimal("999")); monthly.setPeriodDays(365); monthly.setAvailable(false); tariffs.save(monthly);
        new SubscriptionStatusJob(subscriptions, Clock.fixed(Instant.parse("2027-01-01T09:00:00Z"), ZoneId.of("Europe/Moscow"))).execute();
        var stored = subscriptions.findActiveById(s.getId()).orElseThrow();
        assertThat(stored.getTotal()).isEqualByComparingTo("200");
        assertThat(stored.getEndsAt()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(balance()).isEqualByComparingTo("300");
        assertThat(revenueForClient()).isEqualByComparingTo("200");
    }
    @Test void financialFieldsOfPostedDocumentsCannotBeEditedWithoutUnposting() {
        var p = pay("500");
        p = payments.findActiveById(p.getId()).orElseThrow();
        p.setAmount(new BigDecimal("1000"));
        Payment changed = p;
        assertThatThrownBy(() -> payments.save(changed)).isInstanceOf(RuntimeException.class);
        var s = subscriptions.save(subscription(line(monthly, 1)));
        posting.post(s);
        var stored = subscriptions.findActiveById(s.getId()).orElseThrow();
        stored.getLines().getFirst().setPeriods(2);
        assertThatThrownBy(() -> subscriptions.save(stored)).isInstanceOf(RuntimeException.class);
        assertThat(balance()).isEqualByComparingTo("400");
    }
    @Test void cancellingDraftDoesNotTouchMoneyAndRequiresReason() {
        var s = subscriptions.save(subscription(line(monthly, 1)));
        assertThatThrownBy(() -> cancellation.cancel(s.getId(), " ")).isInstanceOf(IllegalArgumentException.class);
        cancellation.cancel(s.getId(), "Передумал");
        assertThat(balance()).isZero();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test void draftSaveRefreshesTariffPriceAndDurationAndIgnoresSubmittedTotals() {
        var s = subscriptions.save(subscription(line(monthly, 1)));
        monthly.setPrice(new BigDecimal("150")); monthly.setPeriodDays(45); tariffs.save(monthly);
        s.setTotal(new BigDecimal("1")); s.getLines().getFirst().setPrice(new BigDecimal("1"));
        s = subscriptions.save(s);
        assertThat(s.getTotal()).isEqualByComparingTo("150");
        assertThat(s.getEndsAt()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test void datesAreDefaultedAndExplicitDatesArePreserved() {
        var s = new Subscription();
        assertThat(s.getDate()).isNotNull(); assertThat(s.getStartsAt()).isNotNull();
        s.setDate(null); s.setStartsAt(null); s.onFilling();
        assertThat(s.getDate()).isNotNull(); assertThat(s.getStartsAt()).isNotNull();
        s.setDate(LocalDateTime.of(2026, 1, 1, 12, 0)); s.setStartsAt(LocalDate.of(2026, 2, 1));
        s.onFilling();
        assertThat(s.getDate()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
        assertThat(s.getStartsAt()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test void unpostingSubscriptionReversesMoneyAndRevenue() {
        pay("500");
        var s = subscriptions.save(subscription(line(monthly, 2)));
        posting.post(s); posting.unpost(s);
        assertThat(balance()).isEqualByComparingTo("500");
        assertThat(revenueForClient()).isZero();
        assertThat(subscriptions.findActiveById(s.getId()).orElseThrow().isPosted()).isFalse();
    }

    @Test void directCancellationOfPostedDocumentIsRejectedToProtectAccounting() {
        pay("500"); var s = subscriptions.save(subscription(line(monthly, 1))); posting.post(s);
        var loaded = subscriptions.findActiveById(s.getId()).orElseThrow();
        loaded.setStatus(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> subscriptions.save(loaded)).isInstanceOf(RuntimeException.class);
        assertThat(balance()).isEqualByComparingTo("400");
        assertThat(revenueForClient()).isEqualByComparingTo("100");
    }

    @Test void simultaneousSubscriptionsCannotOverdrawAccount() throws Exception {
        pay("500");
        var first = subscriptions.save(subscription(line(monthly, 4)));
        var second = subscriptions.save(subscription(line(monthly, 4)));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var s : List.of(first, second)) results.add(executor.submit(() -> {
                ready.countDown(); start.await();
                try { posting.post(s); return true; }
                catch (RuntimeException rejected) { return false; }
            }));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successful = 0;
            for (var result : results) if (result.get(15, java.util.concurrent.TimeUnit.SECONDS)) successful++;
            assertThat(successful).isEqualTo(1);
        } finally { start.countDown(); }
        assertThat(balance()).isEqualByComparingTo("100");
        assertThat(revenueForClient()).isEqualByComparingTo("400");
    }
}
