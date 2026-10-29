package dev.sj010.ssjanitor.core.constants

/**
 * Sshot has two power states:
 * - [RUNNING]: monitoring screenshots, overlay prompts and cleanup all active.
 * - [PAUSED]:  fully stopped — no screenshot detection, no service, no
 *              background work. Pausing behaves like turning the app off.
 */
enum class AppMode {
    RUNNING,
    PAUSED
}
