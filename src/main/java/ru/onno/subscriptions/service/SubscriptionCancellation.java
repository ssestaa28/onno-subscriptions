package ru.onno.subscriptions.service;

import org.springframework.stereotype.Service;
import ru.onno.subscriptions.model.SubscriptionStatus;
import ru.onno.subscriptions.repository.SubscriptionRepository;
import su.onno.posting.PostingService;
import java.util.UUID;

@Service
public class SubscriptionCancellation {
    private final SubscriptionRepository subscriptions;
    private final PostingService posting;
    public SubscriptionCancellation(SubscriptionRepository subscriptions, PostingService posting) {
        this.subscriptions = subscriptions;
        this.posting = posting;
    }
    public void cancel(UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Укажите причину отмены");
        if (reason.length() > 1000) throw new IllegalArgumentException("Причина отмены: не более 1000 символов");
        var subscription = subscriptions.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) return;
        subscription.cancel(reason.trim());
        if (subscription.isPosted()) {
            // Atomic repost reverses the old financial movements and persists cancelled state.
            // If reversal fails, both the original subscription and its movements remain intact.
            posting.repost(subscription);
        } else {
            subscriptions.save(subscription);
        }
    }
}
