# Memora Stage 5/6 Notification Fixes

Implemented in this artifact:

- Capture save persists Memory + initial Reminder atomically through Room.
- Notification scheduling is now treated as a required external side effect; scheduler failure triggers best-effort cancellation and database rollback.
- Temporary capture image is retained on save/scheduling failure so the user can retry.
- `CancellationException` is rethrown so ViewModel cancellation is not converted into a UI failure.
- Reminder notifications continue one day at a time while status is `REMINDER_ACTIVE`, stopping on `DUE_TODAY` or `OVERDUE`.
- Next-day work is appended to the unique WorkManager chain before today's notification is posted, avoiding duplicate current-day notifications if next scheduling fails and the worker retries.
- Missing reminders are treated as deleted and never generate synthetic notifications.
- Completing a reminder explicitly cancels its scheduled WorkManager chain.
- Deleting a Memory cancels all known reminder chains before deleting the Memory.
- Exact-trigger scheduling uses an Instant-based delay, avoiding device-time-zone date conversion errors.

## Verification limitation

This environment does not contain the Android SDK/Gradle distribution and has no network access, so a fresh Gradle build/test execution could not be performed here. The source and test changes were reviewed statically and all known constructor/call-site references were updated. The ZIP must still be built with the project's Android/Gradle environment before treating build/test execution as independently verified.
