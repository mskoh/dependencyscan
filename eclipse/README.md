# Dependency Scan — Eclipse Plugin

Package Explorer / Project Explorer에서 프로젝트 또는 폴더(예: `src`, `src/main`, `src/main/java`)를 우클릭한 뒤 **Dependency Scan**을 선택합니다.
리포트는 프로젝트 `reports/` 디렉터리에 선택 경로 기반 파일명으로 저장됩니다. (예: `src_main_java.md`)
리포트의 Java 버전은 Maven/Gradle 설정을 먼저 확인하고, 없으면 Eclipse 프로젝트의 JDT 컴파일 설정 또는 `JRE System Library`를 사용합니다.

## 설정

**Window → Preferences → Dependency Scan**에서 리포트 저장 위치를 직접 입력하거나 **Browse...**로 폴더를 선택할 수 있습니다.

- 설정명: `Report directory`
- 기본값: `reports`
- 상대 경로: 프로젝트 루트 기준으로 저장
- 절대 경로: 지정한 파일 시스템 경로에 저장
- 설정명: `Custom catalog JSON`
- 비워두면 내장 카탈로그만 사용
- JSON 파일을 지정하면 내장 카탈로그와 병합
- 같은 duplicate group id는 사용자 카탈로그가 덮어씀

## 개발 (Eclipse PDE)

1. Eclipse IDE for RCP and RAP Developers (또는 PDE 포함 배포판) 실행
2. **File → Import → Existing Projects into Workspace**
3. `eclipse` 선택 (`dependencyscan.eclipse`)
4. 플러그인 프로젝트로 Open
5. `dependencyscan.eclipse`를 선택하고 **Run As → Eclipse Application**

## 배포 JAR 설치

배포용 JAR은 다음 경로에 생성합니다.

```text
../deploy/plugins/dependencyscan.eclipse_0.1.0.jar
```

macOS Eclipse에서는 JAR을 `dropins` 폴더에 복사한 뒤 Eclipse를 재시작합니다.

```bash
cp ../deploy/plugins/dependencyscan.eclipse_0.1.0.jar /Applications/Eclipse.app/Contents/Eclipse/dropins/
/Applications/Eclipse.app/Contents/MacOS/eclipse -clean
```

기존 버전을 교체할 때는 같은 이름의 JAR을 덮어쓴 뒤 `-clean`으로 한 번 실행합니다.

## 배포 JAR 생성

Eclipse PDE에서 **File → Export → Plug-in Development → Deployable plug-ins and fragments**를 선택합니다.

- `Package plug-ins as individual JAR archives` 체크
- 대상 디렉터리 예: `../deploy`
- Java 컴파일 타깃은 `build.properties`와 `.settings/org.eclipse.jdt.core.prefs`에서 Java 17로 고정

명령어로 직접 묶을 때는 다음 형태를 사용합니다.

```bash
jar --create --file ../deploy/plugins/dependencyscan.eclipse_0.1.0.jar --manifest META-INF/MANIFEST.MF -C bin . -C . plugin.xml -C . recommendations.json -C . icons
```

## 아이콘

`icons/scan.png`, `icons/scan@2x.png`, `icons/scan-128.png`는 RGBA PNG이며 배경은 투명 처리되어 있습니다.

## 결과

스캔이 끝나면 **Dependency Scan Report** 뷰에 Markdown 형식 리포트가 표시됩니다.

- Libraries in use
- Duplicates (동일 목적 유틸 라이브러리 중복)
- Findings by source: Line / Library / Method / Recommendation
