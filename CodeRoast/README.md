# CodeRoast: A Java Code Roaster

Paste Java code and get a Shame Score, a letter grade, and line-by-line roasts.
No AI involved. It runs on a **rule engine** built with Spring dependency injection.

## How it works
- `Rule` is a tiny interface. Every roast is its own `@Component` class.
- `RoastEngine` receives `List<Rule>` from Spring, runs every rule, sorts the findings by line, and scores them.
- Penalties are capped per rule so one repeated mistake can't zero your score.
- Adding a new roast = adding one small class. No other code changes.

## Rules included
Empty catch blocks, `println` spam, `Thread.sleep`, `printStackTrace`, `== true`, bad variable names,
magic numbers, TODO graveyards, commented-out code, deep nesting, and overlong lines.

## Tech
Java 17 · Spring Boot 3 · JUnit 5 · Vanilla JS

## Run
```bash
mvn spring-boot:run
# open http://localhost:8082
mvn test
```

## API
`POST /api/roast` with `{ "code": "..." }` returns score, grade, title, and findings.

## Roadmap
Real parsing with JavaParser (AST instead of regex) · More rules · Shareable result links · Docker
