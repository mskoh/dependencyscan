# Dependency Scan — VS Code Extension

Explorer에서 프로젝트 또는 폴더(`src`, `src/main`, `src/main/java` 등)를 우클릭한 뒤 **Dependency Scan**을 선택합니다.
리포트는 해당 프로젝트의 `reports/` 디렉터리에 선택 경로 기반 파일명으로 저장됩니다. (예: `src_main_java.md`)

## 설정

VS Code 설정에서 `dependencyscan.reportDirectory`를 변경하면 리포트 저장 위치를 바꿀 수 있습니다.

- 기본값: `reports`
- 상대 경로: 프로젝트 루트 기준으로 저장
- 절대 경로: 지정한 파일 시스템 경로에 저장

`dependencyscan.catalogPath`에 팀/사용자 정의 추천 카탈로그 JSON 경로를 지정할 수 있습니다.

- 비워두면 내장 카탈로그만 사용
- JSON 파일을 지정하면 내장 카탈로그와 병합
- 같은 duplicate group id는 사용자 카탈로그가 덮어씀

## 개발

```bash
cd vscode
npm install
npm run compile
```

저장소 루트에서 F5 (`Run Dependency Scan Extension`)로 Extension Development Host를 띄운 뒤,
`samples/src/main/java` 폴더를 우클릭해 스캔해 보세요.

## 동작

1. 선택한 디렉터리 하위 모든 `.java` 파일 스캔
2. import 기반 외부 라이브러리 메소드 호출 수집
3. Maven/Gradle, `.java-version`, Eclipse JDT 설정에서 Java 버전 감지
4. `core/recommendations.json` 규칙으로 Java 버전별 추천·중복 탐지
5. 중복 유틸 사용 시 목적별 `Matched methods`, 권장 타입, 메소드 치환 제안 표시
6. Markdown 문서 + Webview 리포트 표시
