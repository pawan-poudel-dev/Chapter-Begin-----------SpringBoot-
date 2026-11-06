package com.coderoast;

import java.util.List;

public record RoastResult(int score, String grade, String title, String verdict, int lines, List<Finding> findings) {}
