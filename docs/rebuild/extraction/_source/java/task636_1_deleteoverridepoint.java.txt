/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task636 "_DeleteOverridePoint"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L28994-L29039; <code>474</code> at L28993
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %par1
 * ============================================================================ */
/* --- Java Code to Show a Native Overlay Dialog --- */
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

final String combinedData = "%par1";
String[] parts = combinedData.split(",");
final String lux = (parts.length > 0) ? parts[0] : "Error";
final String brightness = (parts.length > 1) ? parts[1] : "Error";

new Handler(Looper.getMainLooper()).post(new Runnable() {
    public void run() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Delete override point?");
        builder.setMessage("Are you sure you want to remove this data point?\n\nLux: " + lux + ", Brightness: " + brightness);

        /* "Yes" button sets a global variable to "yes" */
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                tasker.setVariable("AAB_JavaDialogResponse", "yes");
                dialog.dismiss();
            }
        });

        /* "No" button sets the same variable to "no" */
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                tasker.setVariable("AAB_JavaDialogResponse", "no");
                dialog.dismiss();
            }
        });

        /* Ensure the variable is set if the dialog is cancelled */
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                tasker.setVariable("AAB_JavaDialogResponse", "cancelled");
            }
        });

        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.show();
    }
});
