package ru.onno.subscriptions.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Translate onno's transactional account-balance rejection into a business HTTP response. */
@RestControllerAdvice
public class PostingErrorAdvice {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> accountBalance(IllegalStateException exception) {
        String message = exception.getMessage();
        if (message != null && message.startsWith("Insufficient ")
                && message.contains("\"PersonalAccounts\"")
                && message.endsWith("Posting would result in negative balance.")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "insufficient_funds",
                    "message", "Недостаточно средств на лицевом счёте. Пополните счёт и повторите проведение."));
        }
        // Do not misclassify unrelated programming or infrastructure failures as balance errors.
        throw exception;
    }
}
