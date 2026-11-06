package com.coderoast;

import java.util.List;

/** One way your code can disappoint us. Every implementation is auto-discovered by Spring. */
public interface Rule {
    List<Finding> check(List<String> lines);
}
