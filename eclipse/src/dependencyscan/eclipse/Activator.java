package dependencyscan.eclipse;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import dependencyscan.eclipse.preferences.PreferenceConstants;

public class Activator extends AbstractUIPlugin {

  public static final String PLUGIN_ID = "dependencyscan.eclipse";

  private static Activator plugin;

  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    plugin = this;
    getPreferenceStore().setDefault(
        PreferenceConstants.REPORT_DIRECTORY,
        PreferenceConstants.DEFAULT_REPORT_DIRECTORY);
    getPreferenceStore().setDefault(PreferenceConstants.CUSTOM_CATALOG_PATH, "");
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    plugin = null;
    super.stop(context);
  }

  public static Activator getDefault() {
    return plugin;
  }
}
