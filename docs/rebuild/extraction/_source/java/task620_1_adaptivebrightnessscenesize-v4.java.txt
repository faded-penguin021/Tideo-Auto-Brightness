/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task620 "_AdaptiveBrightnessSceneSize V4"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L26401-L26468; <code>474</code> at L26400
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/* 1. Get current screen width for scaling. */
wm = context.getSystemService(Context.WINDOW_SERVICE);
dm = new DisplayMetrics();
wm.getDefaultDisplay().getRealMetrics(dm);
screenW = dm.widthPixels;

/* 2. Calculate the magic inverse ratio. */
invRatio = 1440.0 / screenW;

/* 3. Retrieve element lists defined in Tasker variables. */
h1_list = tasker.getVariable("ui_h1_elements");
body_list = tasker.getVariable("ui_body_elements");
info_list = tasker.getVariable("ui_info_elements");

/* 4. Prepare master lists for mapping names to sizes. */
names = new ArrayList();
sizes = new ArrayList();

/* Tier: Headers (Base 22). */
if (h1_list != null && h1_list.length() > 0) {
    h1_size = Math.round(22 * invRatio);
    parts = h1_list.split(",");
    for (i = 0; i < parts.length; i++) {
        names.add(parts[i].trim());
        sizes.add(String.valueOf(h1_size));
    }
}

/* Tier: Info/Medium (Base 19). */
if (info_list != null && info_list.length() > 0) {
    info_size = Math.round(19 * invRatio);
    parts = info_list.split(",");
    for (i = 0; i < parts.length; i++) {
        names.add(parts[i].trim());
        sizes.add(String.valueOf(info_size));
    }
}

/* Tier: Body/Labels (Base 17). */
if (body_list != null && body_list.length() > 0) {
    body_size = Math.round(17 * invRatio);
    parts = body_list.split(",");
    for (i = 0; i < parts.length; i++) {
        names.add(parts[i].trim());
        sizes.add(String.valueOf(body_size));
    }
}

/* 5. Join lists into strings for Tasker Variable Split. */
String join(List list) {
    sb = new StringBuilder();
    for (i = 0; i < list.size(); i++) {
        sb.append(list.get(i));
        if (i < list.size() - 1) {
            sb.append(",");
        }
    }
    return sb.toString();
}

tasker.setVariable("ui_txt_names", join(names));
tasker.setVariable("ui_txt_sizes", join(sizes));
