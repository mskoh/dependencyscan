# dependencyscan

Java 프로젝트 소스를 스캔해 **dependency 라이브러리 메소드 사용**을 분석하고, 중복 사용·대체 가능 메소드를 리포트하는 IDE 플러그인입니다.

## 목적

- 선택한 디렉터리(예: `src/main/java`) 하위 모든 `.java` 소스 스캔
- 사용 중인 라이브러리·메소드 열거
- 동일/유사 기능의 **중복 사용** 탐지
- 프로젝트 **Java 버전**에 맞는 표준 API·라이브러리 메소드 추천
- Java 버전 감지: Maven/Gradle 설정, `.java-version`, Eclipse JDT/JRE 설정
- 추천 메소드 표시는 검증된 카탈로그 매핑이 있는 경우에만 수행
- Duplicates는 가능한 경우 메서드 목적 단위로 판정해 `Matched methods`와 수정 제안을 함께 표시
- 내장 카탈로그에 사용자/팀 카탈로그 JSON을 병합해 규칙 확장 가능
- 결과 리포트: `소스명 → 사용 메소드/라이브러리 → 추천`

## 디렉터리 구조

```
dependencyscan/
├── core/          # 공통 추천 규칙·카탈로그 (JSON)
├── eclipse/       # Eclipse 플러그인
├── vscode/        # VS Code 확장
├── samples/       # 스캔 데모용 Java 샘플
└── README.md
```

## 사용 방법

### VS Code

1. `vscode`에서 의존성 설치 후 확장 실행 (F5)
2. Explorer에서 프로젝트 또는 폴더(예: `src`, `src/main`, `src/main/java`) **우클릭 → Dependency Scan**
3. 리포트가 프로젝트 `reports/`에 저장되고(예: `src_main_java.md`), 에디터·결과 패널에서 확인

```bash
cd vscode
npm install
npm run compile
# VS Code에서 F5로 Extension Development Host 실행
```

### Eclipse

1. Eclipse PDE에서 `eclipse`를 플러그인 프로젝트로 import
2. Package/Project Explorer에서 프로젝트 또는 폴더(`src`, `src/main`, `src/main/java` 등) **우클릭 → Dependency Scan**
3. 리포트가 프로젝트 `reports/`에 저장되고, Dependency Scan 뷰·에디터에서 확인

리포트 저장 위치는 **Window → Preferences → Dependency Scan → Report directory**에서 직접 입력하거나 **Browse...**로 폴더를 선택해 변경할 수 있습니다.
기본값은 `reports`이며, 상대 경로는 프로젝트 루트 기준으로 저장되고 절대 경로는 지정한 위치에 저장됩니다.
사용자/팀 추천 규칙은 **Custom catalog JSON**에 JSON 파일 경로를 지정해 내장 카탈로그와 병합할 수 있습니다.

#### Eclipse 배포 JAR 사용

배포용 JAR:

```text
deploy/plugins/dependencyscan.eclipse_0.1.0.jar
```

macOS Eclipse의 `dropins`에 복사한 뒤 Eclipse를 재시작합니다.

```bash
cp deploy/plugins/dependencyscan.eclipse_0.1.0.jar /Applications/Eclipse.app/Contents/Eclipse/dropins/
/Applications/Eclipse.app/Contents/MacOS/eclipse -clean
```

`-clean`은 Eclipse 플러그인 캐시를 갱신할 때 사용합니다.

## 스캔 결과 항목

| 항목 | 설명 |
|------|------|
| Source | 스캔된 `.java` 파일 경로 |
| Library | import / FQN 기준 라이브러리 |
| Method | 호출된 메소드 |
| Recommendation | Java 버전·표준 API 기준 추천 |
| Duplicates | 동일 목적 기능의 중복 라이브러리 사용 |

## 라이선스

MIT
