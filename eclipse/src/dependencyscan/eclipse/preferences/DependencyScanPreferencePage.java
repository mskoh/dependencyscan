package dependencyscan.eclipse.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import dependencyscan.eclipse.Activator;

public class DependencyScanPreferencePage extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {

  public DependencyScanPreferencePage() {
    super(GRID);
    setPreferenceStore(Activator.getDefault().getPreferenceStore());
    setDescription("Dependency Scan report settings");
  }

  @Override
  public void init(IWorkbench workbench) {
    // Preference store is initialized in the constructor.
  }

  @Override
  protected void createFieldEditors() {
    addField(new ReportDirectoryFieldEditor(
        PreferenceConstants.REPORT_DIRECTORY,
        "Report directory:",
        getFieldEditorParent()));
    FileFieldEditor customCatalog = new FileFieldEditor(
        PreferenceConstants.CUSTOM_CATALOG_PATH,
        "Custom catalog JSON:",
        true,
        getFieldEditorParent());
    customCatalog.setFileExtensions(new String[] { "*.json" });
    addField(customCatalog);
  }

  private static class ReportDirectoryFieldEditor extends StringButtonFieldEditor {

    ReportDirectoryFieldEditor(String name, String labelText, org.eclipse.swt.widgets.Composite parent) {
      super(name, labelText, parent);
      setChangeButtonText("Browse...");
    }

    @Override
    protected String changePressed() {
      Shell shell = getShell();
      DirectoryDialog dialog = new DirectoryDialog(shell);
      dialog.setText("Select Dependency Scan report directory");
      dialog.setMessage("Choose where Dependency Scan report files will be saved.");

      String current = getTextControl().getText().trim();
      if (!current.isEmpty() && java.nio.file.Path.of(current).isAbsolute()) {
        dialog.setFilterPath(current);
      }

      String selected = dialog.open();
      return selected != null ? selected : current;
    }

    @Override
    protected boolean checkState() {
      String value = getTextControl().getText();
      if (value == null || value.trim().isEmpty()) {
        showErrorMessage("Report directory is required.");
        return false;
      }
      clearErrorMessage();
      return true;
    }
  }
}
