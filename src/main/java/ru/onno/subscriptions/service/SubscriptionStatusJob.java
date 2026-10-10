package ru.onno.subscriptions.service;

import org.springframework.stereotype.Component;
import ru.onno.subscriptions.repository.SubscriptionRepository;
import su.onno.annotations.ScheduledJob;
import su.onno.jobs.BackgroundTask;
import java.time.Clock;
import java.time.LocalDate;

@Component
@ScheduledJob(name = "subscription-statuses", cron = "*/5 * * * *")
public class SubscriptionStatusJob implements BackgroundTask {
    private final SubscriptionRepository subscriptions;
    private final Clock clock;
    public SubscriptionStatusJob(SubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }
    @Override public void execute() {
        LocalDate today = LocalDate.now(clock);
        for (var subscription : subscriptions.findAllActive()) {
            if (subscription.getStartsAt() == null || subscription.getEndsAt() == null) continue;
            var next = subscription.statusOn(today);
            if (subscription.getStatus() != next) {
                subscription.setStatus(next);
                subscriptions.save(subscription);
            }
        }
    }
}
