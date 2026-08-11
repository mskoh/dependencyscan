# Dependency Scan — Eclipse Plugin

Package Explorer / Project Explorer에서 폴더(예: `src/main/java`)를 우클릭한 뒤 **Dependency Scan**을 선택합니다.

## 개발 (Eclipse PDE)

1. Eclipse IDE for RCP and RAP Developers (또는 PDE 포함 배포판) 실행
2. **File → Import → Existing Projects into Workspace**
3. `eclipse` 선택 (`dependencyscan.eclipse`)
4. 플러그인 프로젝트로 Open
5. `dependencyscan.eclipse`를 선택하고 **Run As → Eclipse Application**

## 결과

스캔이 끝나면 **Dependency Scan Report** 뷰에 Markdown 형식 리포트가 표시됩니다.

- Libraries in use
- Duplicates (동일 목적 유틸 라이브러리 중복)
- Findings by source: Line / Library / Method / Recommendation
