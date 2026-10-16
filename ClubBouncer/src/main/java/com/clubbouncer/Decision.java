package com.clubbouncer;

/** The bouncer's answer: in or out, how many spots are left, and how long to wait if you're out. */
public record Decision(boolean allowed, int remaining, long retryAfterMillis) {}
