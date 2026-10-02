package com.coderoast;

public record Finding(int line, String rule, String snippet, String roast, int penalty) {}
