package dependencyscan.eclipse.handlers;

import java.nio.file.Path;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import dependencyscan.eclipse.Activator;
import dependencyscan.eclipse.model.ScanReport;
import dependencyscan.eclipse.scanner.DependencyScanner;
import dependencyscan.eclipse.scanner.RecommendationCatalog;
import dependencyscan.eclipse.views.ReportView;

public class ScanHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IContainer container = resolveContainer(HandlerUtil.getCurrentSelection(event));
    if (container == null) {
      MessageDialog.openWarning(
          HandlerUtil.getActiveShell(event),
          "Dependency Scan",
          "스캔할 폴더를 선택하세요. (예: src/main/java)");
      return null;
    }

    Path root = container.getLocation().toFile().toPath();

    Job job = new Job("Dependency Scan") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Scanning " + root, IProgressMonitor.UNKNOWN);
        try {
          RecommendationCatalog catalog = DependencyScanner.loadBundledCatalog();
          DependencyScanner scanner = new DependencyScanner(catalog);
          ScanReport report = scanner.scan(root);
          PlatformUI.getWorkbench().getDisplay().asyncExec(() -> showReport(report));
          return Status.OK_STATUS;
        } catch (Exception ex) {
          return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Scan failed", ex);
        } finally {
          monitor.done();
        }
      }
    };
    job.setUser(true);
    job.schedule();
    return null;
  }

  private void showReport(ScanReport report) {
    try {
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      ReportView view = (ReportView) page.showView(ReportView.ID);
      view.setReport(report);
    } catch (Exception ex) {
      MessageDialog.openError(null, "Dependency Scan", ex.getMessage());
    }
  }

  private IContainer resolveContainer(ISelection selection) {
    if (!(selection instanceof IStructuredSelection)) {
      return null;
    }
    Object element = ((IStructuredSelection) selection).getFirstElement();
    if (element instanceof IContainer) {
      return (IContainer) element;
    }
    if (element instanceof IAdaptable) {
      IResource resource = ((IAdaptable) element).getAdapter(IResource.class);
      if (resource instanceof IContainer) {
        return (IContainer) resource;
      }
      if (resource != null) {
        return resource.getParent();
      }
    }
    return null;
  }
}
