package dependencyscan.eclipse.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import dependencyscan.eclipse.model.ScanReport;
import dependencyscan.eclipse.scanner.DependencyScanner;

public class ReportView extends ViewPart {

  public static final String ID = "dependencyscan.eclipse.reportView";

  private StyledText text;

  @Override
  public void createPartControl(Composite parent) {
    parent.setLayout(new FillLayout());
    text = new StyledText(parent, SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
    text.setText(
        "프로젝트 또는 폴더(예: src, src/main, src/main/java)를 우클릭한 뒤 Dependency Scan을 실행하세요.\n"
            + "리포트는 프로젝트 reports/ 디렉터리에 저장됩니다.");
  }

  public void setReport(ScanReport report) {
    setReport(report, null);
  }

  public void setReport(ScanReport report, String reportPath) {
    if (text == null || text.isDisposed()) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    if (reportPath != null && !reportPath.isBlank()) {
      sb.append("Saved: ").append(reportPath).append("\n\n");
    }
    sb.append(DependencyScanner.formatMarkdown(report));
    text.setText(sb.toString());
  }

  @Override
  public void setFocus() {
    if (text != null && !text.isDisposed()) {
      text.setFocus();
    }
  }
}
