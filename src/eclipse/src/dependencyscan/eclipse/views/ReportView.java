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
    text.setText("폴더를 우클릭한 뒤 Dependency Scan을 실행하세요.\n예: src/main/java");
  }

  public void setReport(ScanReport report) {
    if (text == null || text.isDisposed()) {
      return;
    }
    text.setText(DependencyScanner.formatMarkdown(report));
  }

  @Override
  public void setFocus() {
    if (text != null && !text.isDisposed()) {
      text.setFocus();
    }
  }
}
