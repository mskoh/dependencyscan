# Dependency Scan — VS Code Extension

Explorer에서 Java 소스 폴더를 우클릭한 뒤 **Dependency Scan**을 선택합니다.

## 개발

```bash
cd src/vscode
npm install
npm run compile
```

저장소 루트에서 F5 (`Run Dependency Scan Extension`)로 Extension Development Host를 띄운 뒤,
`samples/src/main/java` 폴더를 우클릭해 스캔해 보세요.

## 동작

1. 선택한 디렉터리 하위 모든 `.java` 파일 스캔
2. import 기반 외부 라이브러리 메소드 호출 수집
3. `src/core/recommendations.json` 규칙으로 Java 버전별 추천·중복 탐지
4. Markdown 문서 + Webview 리포트 표시
