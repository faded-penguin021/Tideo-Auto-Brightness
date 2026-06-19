/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task625 "_AppPicker"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L27186-L27261; <code>474</code> at L27185
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.ArrayList;

/* Get PackageManager */
pm = context.getPackageManager();

/* Get Apps */
List packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
JSONArray appArray = new JSONArray();

for (int i = 0; i < packages.size(); i++) {
    ApplicationInfo packageInfo = (ApplicationInfo) packages.get(i);
    
    /* Filter: Only apps that can be launched */
    if (pm.getLaunchIntentForPackage(packageInfo.packageName) != null) {
        JSONObject appObj = new JSONObject();
        
        /* Basic Info */
        appObj.put("n", pm.getApplicationLabel(packageInfo).toString());
        appObj.put("p", packageInfo.packageName);
        
        /* Icon Processing */
        Bitmap bitmap = null;
        Bitmap resized = null;
        try {
            Drawable icon = packageInfo.loadIcon(pm);
            
            if (icon instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) icon).getBitmap();
            } else {
                /* Create canvas for adaptive icons */
                bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
            }
            
            /* RESIZE: 48x48 for performance */
            resized = Bitmap.createScaledBitmap(bitmap, 48, 48, false);
            
            /* Convert to Base64 String */
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] imageBytes = baos.toByteArray();
            String encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            baos.close();
            
            appObj.put("i", encodedImage);
            
        } catch (Exception e) {
            /* Ignore icon errors */
        } finally {
            /* CRITICAL: Explicitly recycle to free native memory immediately */
            if (resized != null && !resized.isRecycled()) {
                resized.recycle();
            }
            if (bitmap != null && !bitmap.isRecycled() && !(packageInfo.loadIcon(pm) instanceof BitmapDrawable)) {
                bitmap.recycle();
            }
        }
        
        appArray.put(appObj);
    }
}

tasker.setVariable("app_list_json", appArray.toString());
