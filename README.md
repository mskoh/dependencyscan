# dependencyscan

Java 프로젝트 소스를 스캔해 **dependency 라이브러리 메소드 사용**을 분석하고, 중복 사용·대체 가능 메소드를 리포트하는 IDE 플러그인입니다.

## 목적

- 선택한 디렉터리(예: `src/main/java`) 하위 모든 `.java` 소스 스캔
- 사용 중인 라이브러리·메소드 열거
- 동일/유사 기능의 **중복 사용** 탐지
- 프로젝트 **Java 버전**에 맞는 표준 API·라이브러리 메소드 추천
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
2. Explorer에서 폴더(예: `src/main/java`) **우클릭 → Dependency Scan**
3. 결과 패널에서 소스별 라이브러리/메소드·추천·중복 리포트 확인

```bash
cd vscode
npm install
npm run compile
# VS Code에서 F5로 Extension Development Host 실행
```

### Eclipse

1. Eclipse PDE에서 `eclipse`를 플러그인 프로젝트로 import
2. Package Explorer에서 폴더 **우클릭 → Dependency Scan**
3. Dependency Scan 뷰에서 리포트 확인

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
