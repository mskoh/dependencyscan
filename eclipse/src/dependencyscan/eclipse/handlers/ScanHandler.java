package dependencyscan.eclipse.handlers;

import java.nio.file.Path;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

import dependencyscan.eclipse.Activator;
import dependencyscan.eclipse.model.ScanReport;
import dependencyscan.eclipse.scanner.DependencyScanner;
import dependencyscan.eclipse.scanner.RecommendationCatalog;
import dependencyscan.eclipse.scanner.ReportPaths;
import dependencyscan.eclipse.preferences.PreferenceConstants;
import dependencyscan.eclipse.views.ReportView;

public class ScanHandler extends AbstractHandler {

  private static final String TEXT_EDITOR_ID = "org.eclipse.ui.DefaultTextEditor";

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IContainer container = resolveContainer(HandlerUtil.getCurrentSelection(event));
    if (container == null || container.getLocation() == null) {
      MessageDialog.openWarning(
          HandlerUtil.getActiveShell(event),
          "Dependency Scan",
          "스캔할 폴더를 선택하세요.\n예: 프로젝트, src, src/main, src/main/java");
      return null;
    }

    final IContainer selectedContainer = container;
    final Path scanRoot = container.getLocation().toFile().toPath();
    final IProject project = container.getProject();

    Job job = new Job("Dependency Scan") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Scanning " + scanRoot, IProgressMonitor.UNKNOWN);
        try {
          RecommendationCatalog catalog = DependencyScanner.loadBundledCatalog();
          String customCatalogPath = Activator.getDefault()
              .getPreferenceStore()
              .getString(PreferenceConstants.CUSTOM_CATALOG_PATH);
          DependencyScanner.mergeCustomCatalog(catalog, customCatalogPath);
          DependencyScanner scanner = new DependencyScanner(catalog);
          Integer eclipseJavaVersion = detectEclipseJavaVersion(project);
          ScanReport report = scanner.scan(scanRoot, eclipseJavaVersion);

          Path projectRoot = DependencyScanner.findProjectRoot(scanRoot);
          String markdown = DependencyScanner.formatMarkdown(report);
          String reportDirectory = Activator.getDefault()
              .getPreferenceStore()
              .getString(PreferenceConstants.REPORT_DIRECTORY);
          Path reportFile = ReportPaths.writeReport(projectRoot, scanRoot, reportDirectory, markdown);

          if (project != null && project.exists()) {
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
          }

          PlatformUI.getWorkbench().getDisplay().asyncExec(
              () -> showReport(report, reportFile, selectedContainer.getProject()));
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

  private void showReport(ScanReport report, Path reportFile, IProject project) {
    try {
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
      ReportView view = (ReportView) page.showView(ReportView.ID);
      view.setReport(report, reportFile.toAbsolutePath().toString());

      if (project != null && project.exists()) {
        IFile workspaceFile = findWorkspaceFile(project, reportFile);
        if (workspaceFile != null && workspaceFile.exists()) {
          IDE.openEditor(page, workspaceFile, TEXT_EDITOR_ID, true);
          return;
        }
      }
      IDE.openEditor(
          page,
          new FileStoreEditorInput(EFS.getLocalFileSystem().fromLocalFile(reportFile.toFile())),
          TEXT_EDITOR_ID,
          true);
    } catch (Exception ex) {
      MessageDialog.openError(null, "Dependency Scan", ex.getMessage());
    }
  }

  private IFile findWorkspaceFile(IProject project, Path reportFile) {
    if (project.getLocation() == null) {
      return null;
    }
    Path projectPath = project.getLocation().toFile().toPath().toAbsolutePath().normalize();
    Path filePath = reportFile.toAbsolutePath().normalize();
    if (!filePath.startsWith(projectPath)) {
      return null;
    }
    Path relative = projectPath.relativize(filePath);
    IResource resource = project.findMember(relative.toString().replace('\\', '/'));
    return resource instanceof IFile ? (IFile) resource : null;
  }

  private Integer detectEclipseJavaVersion(IProject project) {
    if (project == null || !project.exists()) {
      return null;
    }
    try {
      if (!project.hasNature(JavaCore.NATURE_ID)) {
        return null;
      }
      IJavaProject javaProject = JavaCore.create(project);
      Integer compliance = parseJavaVersion(javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, false));
      if (compliance != null) {
        return compliance;
      }
      for (IClasspathEntry entry : javaProject.getRawClasspath()) {
        if (entry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
          continue;
        }
        for (String segment : entry.getPath().segments()) {
          Integer version = parseJavaVersion(segment);
          if (version != null) {
            return version;
          }
        }
      }
      compliance = parseJavaVersion(javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
      if (compliance != null) {
        return compliance;
      }
    } catch (Exception ex) {
      return null;
    }
    return null;
  }

  private Integer parseJavaVersion(String value) {
    if (value == null) {
      return null;
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?:JavaSE-|jdk-|jre-|java-)?(?:1\\.)?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(value);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * Resolve a folder from Project Explorer / Package Explorer selection.
   * Supports IProject, IFolder, source folders (src/main/java), packages, and files (parent folder).
   */
  private IContainer resolveContainer(ISelection selection) {
    if (!(selection instanceof IStructuredSelection)) {
      return null;
    }
    Object element = ((IStructuredSelection) selection).getFirstElement();
    if (element == null) {
      return null;
    }

    IContainer fromJava = resolveJavaElement(element);
    if (fromJava != null) {
      return fromJava;
    }

    if (element instanceof IContainer) {
      return (IContainer) element;
    }

    if (element instanceof IResource) {
      return containerFromResource((IResource) element);
    }

    if (element instanceof IAdaptable) {
      IResource resource = ((IAdaptable) element).getAdapter(IResource.class);
      if (resource != null) {
        return containerFromResource(resource);
      }
      IJavaElement javaElement = ((IAdaptable) element).getAdapter(IJavaElement.class);
      if (javaElement != null) {
        return resolveJavaElement(javaElement);
      }
    }
    return null;
  }

  private IContainer resolveJavaElement(Object element) {
    if (!(element instanceof IJavaElement)) {
      return null;
    }
    IJavaElement javaElement = (IJavaElement) element;

    if (javaElement instanceof IJavaProject) {
      return ((IJavaProject) javaElement).getProject();
    }
    if (javaElement instanceof IPackageFragmentRoot) {
      IResource resource = ((IPackageFragmentRoot) javaElement).getResource();
      return containerFromResource(resource);
    }
    if (javaElement instanceof IPackageFragment) {
      IResource resource = ((IPackageFragment) javaElement).getResource();
      return containerFromResource(resource);
    }

    IResource resource = javaElement.getResource();
    return containerFromResource(resource);
  }

  private IContainer containerFromResource(IResource resource) {
    if (resource == null) {
      return null;
    }
    if (resource instanceof IContainer) {
      return (IContainer) resource;
    }
    return resource.getParent();
  }
}
