/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task643 "_LearnWriteSecure"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L30506-L30637; <code>474</code> at L30505
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_Package
 * ============================================================================ */
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.util.function.Consumer;
import io.reactivex.subjects.CompletableSubject;
import com.joaomgcd.taskerm.action.java.JavaCodeException;

/* 
 * 1. Retrieve the target package name from the Tasker variable %AAB_Package.
 *    If the variable is not set, throw an exception to inform the user.
 */
packageName = tasker.getVariable("AAB_Package");
if (packageName == null) {
    throw new JavaCodeException("Variable %AAB_Package is not set. Cannot check permission or generate ADB command.");
}

/*
 * 2. Check if the package already has the permission.
 *    The standard way to check a specific package's permission status (non-runtime)
 *    is using PackageManager. If it's already granted, we exit early.
 */
pm = context.getPackageManager();
if (pm.checkPermission("android.permission.WRITE_SECURE_SETTINGS", packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
    return;
}

/* 3. Construct the ADB command string. */
adbCommand = "adb shell pm grant " + packageName + " android.permission.WRITE_SECURE_SETTINGS";

/* 
 * 4. Create a Subject to block the script execution. 
 *    This ensures the script (and the temporary Activity) stays alive 
 *    until the user interacts with the dialog.
 */
waiter = CompletableSubject.create();

/* 5. Define the UI logic using doWithActivity to access a valid UI Context. */
uiConsumer = new Consumer() {
    accept(Object activityObj) {
        /* Cast the raw object to an Activity. */
        Activity activity = (Activity) activityObj;

        /* 
         * Create the Dialog Builder using the Activity context.
         * We use the fully qualified name to avoid BeanShell inner class resolution issues.
         */
        builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("Permission Required");
        builder.setMessage("To enable more efficient Super Dimming functionality, the 'Write Secure Settings' permission must be granted.\n\nSince this is a system-protected permission, it cannot be granted within the app. You must use a computer with ADB (Android Debug Bridge).\n\nRun the following command:\n\n" + adbCommand);
        
        /* 
         * Define the 'Done' button. 
         * This closes the activity and unblocks the script.
         */
        builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            onClick(DialogInterface dialog, int which) {
                activity.finish();
                waiter.onComplete();
            }
        });

        /* 
         * Define the 'Cancel' button.
         * Same behavior as Done, just closing everything.
         */
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            onClick(DialogInterface dialog, int which) {
                activity.finish();
                waiter.onComplete();
            }
        });

        /* 
         * Define a 'Copy Command' button.
         * We set it here, but we will override its behavior in OnShowListener
         * so that it does NOT dismiss the dialog when clicked.
         */
        builder.setNeutralButton("Copy Command", new DialogInterface.OnClickListener() {
            onClick(DialogInterface dialog, int which) {
                /* Placeholder - will be overridden */
            }
        });

        /* Handle the back button or outside touch dismissal. */
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            onCancel(DialogInterface dialog) {
                activity.finish();
                waiter.onComplete();
            }
        });

        /* Create the dialog instance. */
        dialog = builder.create();

        /* 
         * Use OnShowListener to override the Neutral button's behavior.
         * This allows us to copy text without closing the dialog.
         */
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            onShow(DialogInterface d) {
                Button copyBtn = ((AlertDialog)d).getButton(AlertDialog.BUTTON_NEUTRAL);
                copyBtn.setOnClickListener(new View.OnClickListener() {
                    onClick(View v) {
                        /* Copy to Clipboard. */
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("ADB Command", adbCommand);
                        clipboard.setPrimaryClip(clip);

                        /* Show feedback. */
                        Toast.makeText(activity, "Command copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        /* Show the dialog. */
        dialog.show();
    }
};

/* 6. Execute the UI logic on the main thread. */
tasker.doWithActivity(uiConsumer);

/* 7. Wait here until the user dismisses the dialog (Done, Cancel, or Back). */
waiter.blockingAwait();
