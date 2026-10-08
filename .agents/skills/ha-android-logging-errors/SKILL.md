---
name: ha-android-logging-errors
description: Home Assistant Android logging and error handling. Use when writing catch blocks, logging with Timber, handling exceptions or fallbacks, using FailFast, or reviewing for silent failures and leaked user data.
---

# HA Android Logging and Errors

Use this skill when adding logging, catching exceptions, or defining fallback behavior.

## Timber

All logging goes through Timber (`import timber.log.Timber`). Timber logs are enabled automatically in debug builds and use the class name as tag.

- No periods at the end of log messages.
- Never leak user information in release logs — wrap user data with `sensitive()` instead of manual `BuildConfig.DEBUG` checks. Entity IDs are not sensitive and help debugging; URLs and anything server- or user-identifying are sensitive.
- Error logs follow the shape `Timber.e(e, "Failed to <do what> for <contextual id>")` — include the identifiers needed to debug.
- If building a log message requires heavy logic, only compute it when necessary. Don't log normal, expected behavior, and don't repeat a message already logged nearby.

```kotlin
Timber.e(exception, "Failed to load user dashboard for userId=$userId")
// sensitive() hides user data in release logs
Timber.d("User logged in: userId=${sensitive(user.id)}")
```

## Exceptions

- Never silently ignore an exception — always log it with Timber, including enough context to troubleshoot.
- Catch the most precise exception type, not `catch (e: Exception)`.
- Never catch `kotlin.coroutines.cancellation.CancellationException` — let it propagate to cancel coroutines properly. A broad `catch (e: Exception)` in a suspend function silently catches it too; when a broad catch is unavoidable, rethrow cancellation first:

```kotlin
try {
    riskyOperation()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Timber.e(e, "Failed to update sensors")
}
```
- Thrown exceptions need meaningful messages and explicit types instead of generic ones.
- Don't use `check()`/`require()` for conditions whose exception is then caught and ignored upstream — the message never reaches the logs. Log the problem where it's detected and return early (or surface an error state to the user) instead.

## Failures Are Not Empty Data

Never let a failure become empty or default data. "Couldn't load" shown as "nothing here" (an empty dashboard, an empty drawer, every room empty) is a bug, and worse than an error because the user believes it.

- A failed request, a missing connection, or a response that isn't `success` or has an unexpected shape must not become `[]`, `""`, `emptyMap()` or a placeholder object through `.orEmpty()`, `?: emptyList()`, `getOrDefault`, or a `catch` that returns empty.
- Make failure visible in the type: return `null` (or a sealed result such as `Loaded`/`Failed`) and let the compiler force every caller to decide. Keep "not loaded yet", "failed" and "loaded and empty" distinct; a `StateFlow` that starts at `emptyList()` can't tell them apart, so start it at `null`.
- Decide what failure means for the user: keep the last good data and retry (a refresh that fails must never replace good data), or show an error state with a retry. Log it with context.
- Defaults are fine only for data that is optional by design and absent in a successful response, the way upstream treats it (an entity without an `icon`, a card option left unset).

## FailFast

Use the `FailFast` API from `:common` for offensive programming: it crashes debug builds so issues surface early, and falls back gracefully in production.

```kotlin
val value = FailFast.failOnCatch(
    message = { "Couldn't get ExternalThirdParty value, current state: ${ExternalThirdPartyJavaAPI.state()}" },
    fallback = "fallbackValue",
) { riskyOperation() }
```

In unit tests, a project-wide listener rethrows FailFast failures as test failures — see the `ha-android-testing` skill.

Prefer catching problems at build time over runtime: lean on the Kotlin compiler (exhaustive `when` over sealed types) and consider a custom lint rule in `:lint` when a check can't be expressed at compile time.

## Strict Mode

Strict Mode violations can be ignored with `io.homeassistant.companion.android.common.util.IgnoreViolationRules`, injected in the Application class.

## Debugging Aids

- Filter LogCat by tag: Timber uses the class name as tag by default.
- Debug network requests with OkHttp logging interceptors in debug builds.
- Inspect the Room database with Android Studio's App Inspection tool.
