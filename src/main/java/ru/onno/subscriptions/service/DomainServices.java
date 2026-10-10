package ru.onno.subscriptions.service;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import su.onno.model.CatalogObject;
import su.onno.types.Ref;
import su.onno.types.RefResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Bridge required because onno creates lifecycle domain objects outside Spring. */
@Component
public class DomainServices implements ApplicationContextAware {
    private static volatile ApplicationContext context;

    @Override public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static <T extends CatalogObject> T active(Ref<T> ref) {
        if (ref == null) return null;
        return context.getBean(RefResolver.class).resolve(ref)
                .filter(entity -> !entity.isDeletionMark()).orElse(null);
    }

    public static LocalDate today() { return LocalDate.now(context.getBean(Clock.class)); }
    public static LocalDateTime now() {
        return LocalDateTime.now(context.getBean(Clock.class)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }
    public static <T> T bean(Class<T> type) { return context.getBean(type); }
}
