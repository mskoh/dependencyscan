# Dependency Scan Report

- Scanned root: `/Volumes/ExSSD/dev/git/dependencyscan/samples`
- Java version: 17
- Files scanned: 2
- Generated: 2026-08-11T06:25:52.818903Z

## Libraries in use

| Library | Types | Method calls |
|---|---|---|
| Spring Framework | `CollectionUtils`, `StringUtils` | 3 |
| Apache Commons Lang | `StringUtils` | 3 |
| Guava | `Preconditions`, `Strings`, `Lists` | 3 |
| Apache Commons IO | `FileUtils` | 1 |

## Duplicates

### 문자열 null/empty 검사 및 유틸 (`string-utils`)
- Types: `org.springframework.util.StringUtils`, `org.apache.commons.lang3.StringUtils`, `com.google.common.base.Strings`
- Sources: `src/main/java/com/example/demo/OrderService.java`, `src/main/java/com/example/demo/UserService.java`

### 컬렉션 null/empty 및 변환 유틸 (`collection-utils`)
- Types: `org.springframework.util.CollectionUtils`, `com.google.common.collect.Lists`
- Sources: `src/main/java/com/example/demo/OrderService.java`, `src/main/java/com/example/demo/UserService.java`

## Findings by source

### `src/main/java/com/example/demo/OrderService.java`

| Line | Library | Method | Recommendation |
|---|---|---|---|
| 14 | Spring Framework | `CollectionUtils.isEmpty()` | collection == null || collection.isEmpty() — Spring에 강하게 의존하지 않는 모듈에서는 표준 검사식을 권장합니다. |
| 18 | Spring Framework | `StringUtils.hasText()` | — |
| 21 | Apache Commons Lang | `StringUtils.trim()` | — |
| 25 | Spring Framework | `StringUtils.isEmpty()` | — |
| 26 | Apache Commons Lang | `StringUtils.isBlank()` | java.lang.String#isBlank() / isEmpty() — 단순 null/blank 검사는 표준 API로 대체 가능합니다. |

### `src/main/java/com/example/demo/UserService.java`

| Line | Library | Method | Recommendation |
|---|---|---|---|
| 19 | Apache Commons Lang | `StringUtils.isBlank()` | java.lang.String#isBlank() / isEmpty() — 단순 null/blank 검사는 표준 API로 대체 가능합니다. |
| 22 | Guava | `Strings.isNullOrEmpty()` | java.lang.String#isBlank() — Java 11+ 에서는 String.isBlank() 사용을 권장합니다. |
| 29 | Guava | `Preconditions.checkNotNull()` | java.util.Objects#requireNonNull — 단순 null 검사는 Objects.requireNonNull로 대체 가능합니다. |
| 30 | Guava | `Lists.newArrayList()` | java.util.List#of / new ArrayList<>() — 단순 리스트 생성은 표준 컬렉션 API로 충분합니다. |
| 34 | Apache Commons IO | `FileUtils.readFileToString()` | java.nio.file.Files — Java 11+ NIO Files API로 대부분 대체 가능합니다. |

