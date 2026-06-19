/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task38 "_SuggestCurveParameters V24 (Hybrid)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L9922-L10868; <code>474</code> at L9921
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %d, %s
 * ============================================================================ */
/*
AAB Curve Fitting Engine V43.8 (Confidence Fix)
- Fix: Added explicit log and skip for candidates that violate MaxBright boundary (>1e5 cost).
- Fix: Improved Stage 1 deduplication to update scores for duplicate lux boundaries.
- Fix: Restored pre-blend metric assignments so Inertia Confidence calculates correctly.
- Fix: Recalculates metrics AFTER blending to ensure honest logs.
- Fix: Absolute Failure state aborts properly instead of fake fallback.
- Fix: Brightness inputs clamped to >= 0.0.
- Fix: Replaced 6 duplicated error/bias methods with unified evaluateMetrics().
- Constants: Power=0.33, Z2 Buffer=0.5, RMSE Cost=50.0, Tau=4.0
*/

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

logBuffer = new StringBuffer();
suggestion_made = false;

/* Helper: Safe Power with Floor (Prevents NaN on negative bases) */
double safePowDelta(double val, double p) {
    double delta = Math.max(val, 1e-9);
    return Math.pow(delta, p);
}

/* Helper: Log-Space Weighting (Inverse Lux) */
double getLogWeight(double[] pt) {
    if (pt == null || pt.length < 3) return 0.1;
    if (pt.length > 3 && Math.abs(pt[3] - 3.0) < 0.1) {
        return (pt[2] * 0.05) / (pt[0] + 2.0); 
    }
    return pt[2] / (pt[0] + 2.0);
}

/* Helper: Check if a point is real data (not a ghost point) */
boolean isRealDataPoint(double[] pt) {
    return (pt.length <= 3 || pt[3] < 2.5);
}

/* Helper: Calculate adaptive number of refinement points */
int calculateRefinePoints(double gap_start, double gap_end, double current_pos) {
    double gap_ratio, gap_size;
    boolean is_dark_zone;
    if (gap_start <= 1e-9) return 5;
    gap_ratio = gap_end / gap_start;
    gap_size = gap_end - gap_start;
    is_dark_zone = (current_pos < 100.0);
    
    if (gap_ratio < 1.5 || gap_size < 10.0) return 0;
    if (gap_ratio < 3.0) return is_dark_zone ? 3 : 5;
    return is_dark_zone ? 5 : 8;
}

/* Helper: Generate log-spaced points */
List generateSmartRefinementPoints(double start, double end, double current, int count) {
    List points = new ArrayList();
    double log_start, log_end, log_current, total_span, left_span;
    int left_points, right_points, i;
    double left_step, right_step, val;
    
    if (count <= 0) { points.add(new Double(current)); return points; }
    if (start <= 1e-9 || end <= 1e-9 || start >= end || current < start || current > end) {
        points.add(new Double(current)); return points;
    }

    log_start = Math.log(start); log_end = Math.log(end); log_current = Math.log(current);
    total_span = log_end - log_start;
    
    if (total_span < 1e-9) { points.add(new Double(current)); return points; }

    left_span = log_current - log_start;
    left_points = (int)Math.round(count * (left_span / total_span));
    right_points = count - left_points;
    
    if (left_points > 0 && left_span > 1e-9) {
        left_step = left_span / (left_points + 1);
        for (i = 1; i <= left_points; i++) {
            val = Math.exp(log_start + i * left_step);
            points.add(new Double(val));
        }
    }
    points.add(new Double(current));
    if (right_points > 0 && (log_end - log_current) > 1e-9) {
        right_step = (log_end - log_current) / (right_points + 1);
        for (i = 1; i <= right_points; i++) {
            val = Math.exp(log_current + i * right_step);
            points.add(new Double(val));
        }
    }
    return points;
}

/* UNIFIED HELPER: Calculate R2, nRMSE, and Bias */
double[] evaluateMetrics(int zone, List points, double max_bright, double fA, double fB, double fC, double z1e) {
    double[] res = {-2.0, 0.0, 0.0}; //[R2, nRMSE, Bias]
    if (points.size() == 0) return res;
    
    double sum_w = 0.0, sum_wy = 0.0, ss_tot = 0.0, ss_res = 0.0, sum_sq = 0.0, sum_err = 0.0;
    double w, y, y_pred = 0.0, e; double[] pt; int count_valid = 0;
    
    for(int i=0; i<points.size(); i++) {
        pt = (double[])points.get(i); w = getLogWeight(pt);
        sum_w += w; sum_wy += w * pt[1];
    }
    if (sum_w < 1e-9) return res;
    double mean_y = sum_wy / sum_w;
    
    double term_d2 = 0.0;
    if (zone == 2) term_d2 = safePowDelta(z1e - fC, 0.33);

    for(int i=0; i<points.size(); i++) {
        pt = (double[])points.get(i); w = getLogWeight(pt); y = pt[1];
        if (zone == 1) {
            y_pred = fA * Math.sqrt(pt[0]);
        } else if (zone == 2) {
            if (pt[0] <= fC) continue;
            y_pred = fA + fB * (safePowDelta(pt[0] - fC, 0.33) - term_d2);
        } else { // Zone 3
            y_pred = max_bright - (fA / pt[0]) * max_bright;
        }
        
        e = y_pred - y;
        sum_err += w * e;
        sum_sq += w * e * e;
        ss_tot += w * Math.pow(y - mean_y, 2.0);
        ss_res += w * e * e;
        count_valid++;
    }
    
    if (count_valid == 0) return res;
    
    res[0] = (ss_tot < 1e-9) ? 0.0 : 1.0 - (ss_res / ss_tot);
    res[1] = (max_bright > 1e-9) ? (Math.sqrt(sum_sq / sum_w) / max_bright) : 0.0;
    res[2] = sum_err / sum_w;
    return res;
}

/* Helper: Zone 1 (sqrt) Weighted fit */
double[] fitZone1(List points) {
    double[] result = {0.0, -2.0};
    int i; double sum_wy = 0.0, sum_w = 0.0, mean_y_w, sum_w_xy = 0.0, sum_w_xx = 0.0, param_a, ss_total_w = 0.0, ss_residual_w = 0.0;
    double x, y, w, x_prime, y_pred; double[] pt;

    if (points.size() < 3) return result;

    for (i = 0; i < points.size(); i++) {
        pt = (double[]) points.get(i); w = getLogWeight(pt);
        sum_wy += w * pt[1]; sum_w += w;
    }
    mean_y_w = (sum_w > 1e-9) ? (sum_wy / sum_w) : 0.0;

    for (i = 0; i < points.size(); i++) {
        pt = (double[]) points.get(i);
        x = pt[0]; y = pt[1]; w = getLogWeight(pt);
        x_prime = Math.sqrt(x);
        sum_w_xy += w * x_prime * y; sum_w_xx += w * x_prime * x_prime;
    }

    param_a = (sum_w_xx > 1e-9) ? (sum_w_xy / sum_w_xx) : 0.0;
    result[0] = param_a;

    for (i = 0; i < points.size(); i++) {
        pt = (double[]) points.get(i);
        x = pt[0]; y = pt[1]; w = getLogWeight(pt);
        y_pred = param_a * Math.sqrt(x);
        ss_total_w += w * Math.pow(y - mean_y_w, 2.0);
        ss_residual_w += w * Math.pow(y - y_pred, 2.0);
    }
    if (ss_total_w < 1e-9) result[1] = 0.0; else result[1] = 1.0 - (ss_residual_w / ss_total_w);
    return result;
}

/* Helper: Zone 2 Weighted R²-only fit */
double getR2_Z2_only(List points, double form2c, double form2d, double start_a, double start_b) {
    int i; double sum_wy_valid = 0.0, sum_w_valid = 0.0, mean_y_valid, a, b, term, term_d, w;
    double ss_total_w = 0.0, ss_residual_w = 0.0, y_pred, sum_num = 0.0, sum_den = 0.0;
    double[] pt; List valid_points = new ArrayList();

    if (points.size() < 3) return -2.0;
    if (form2c < -50.0) form2c = -50.0;

    term_d = safePowDelta(form2d - form2c, 0.33);

    for (i = 0; i < points.size(); i++) {
        pt = (double[]) points.get(i);
        if (pt[0] > form2c && form2d > form2c) {
            valid_points.add(pt); w = getLogWeight(pt);
            sum_wy_valid += w * pt[1]; sum_w_valid += w;
        }
    }
    
    if (valid_points.size() < 3) return -2.0;
    mean_y_valid = (sum_w_valid > 1e-9) ? (sum_wy_valid / sum_w_valid) : 0.0;

    a = start_a; 
    for (i = 0; i < valid_points.size(); i++) {
        pt = (double[]) valid_points.get(i); w = getLogWeight(pt);
        term = safePowDelta(pt[0] - form2c, 0.33) - term_d;
        sum_num += w * (pt[1] - a) * term; sum_den += w * term * term;
    }
    if (sum_den > 1e-9) b = sum_num / sum_den; else b = start_b;
    if (b < 0.0) b = 0.01;

    for (i = 0; i < valid_points.size(); i++) {
        pt = (double[]) valid_points.get(i); w = getLogWeight(pt);
        term = safePowDelta(pt[0] - form2c, 0.33) - term_d;
        y_pred = a + b * term;
        ss_total_w += w * Math.pow(pt[1] - mean_y_valid, 2.0);
        ss_residual_w += w * Math.pow(pt[1] - y_pred, 2.0);
    }
    if (ss_total_w < 1e-9) return 0.0;
    return 1.0 - (ss_residual_w / ss_total_w);
}

double calculateSizePenalty(int count) {
    if (count >= 4) return 0.0;
    double diff = 4.0 - count; return 0.25 * diff * diff; 
}

/* Helper: Calculate blended point count based on quality */
double getWeightedCount(int count, double r2, double nrmse) {
    if (count <= 0) return 0.0;
    double r2_safe = (r2 < 0.0) ? 0.0 : r2;
    if (r2_safe > 1.0) r2_safe = 1.0;
    double err_pen = (nrmse > 0.5) ? 0.5 : nrmse;
    double quality = r2_safe * (1.0 - err_pen);
    double weight = 0.25 + 0.75 * quality;
    return count * weight;
}

/* Helper: Analytic Approximation Cost */
double approximateCost(double z1e, double z2e, List dataPoints, int N, double form1a, double old_form2b, double form2c, double max_bright) {
    List z1_pts = new ArrayList(); List z2_pts = new ArrayList(); List z3_pts = new ArrayList();
    int i; double[] pt; 
    double boundary_y2_end, approx_form2a, approx_form3a, z1_nrmse, z2_nrmse, z3_nrmse, z1_bias, z2_bias, z3_bias;
    double sum_num, sum_den, term_x, term_d, best_b, x, w;

    if (z1e >= z2e) return 1e12; 

    for (i = 0; i < N; i++) {
        pt = (double[]) dataPoints.get(i);
        if (pt[0] <= z1e) z1_pts.add(pt);
        else if (pt[0] <= z2e) z2_pts.add(pt);
        else z3_pts.add(pt);
    }
    
    if (z1_pts.size() < 3 || z2_pts.size() < 3) return 1e12;
    if (z3_pts.size() == 0 && N < 12) return 1e12;

    approx_form2a = form1a * Math.sqrt(z1e);
    term_d = safePowDelta(z1e - form2c, 0.33);

    sum_num = 0.0; sum_den = 0.0;
    for (i=0; i<z2_pts.size(); i++) {
        pt = (double[]) z2_pts.get(i); x = pt[0]; w = getLogWeight(pt);
        if (x <= form2c) continue;
        term_x = safePowDelta(x - form2c, 0.33) - term_d;
        sum_num += w * (pt[1] - approx_form2a) * term_x;
        sum_den += w * term_x * term_x;
    }
    if (sum_den > 1e-9) best_b = sum_num / sum_den; else best_b = old_form2b;
    if (best_b < 0.0) best_b = 0.01;

    boundary_y2_end = approx_form2a + best_b * (safePowDelta(z2e - form2c, 0.33) - term_d);
    if (boundary_y2_end > max_bright) return 1e12;
    
    approx_form3a = (max_bright > 0.01) ? (z2e * (max_bright - boundary_y2_end) / max_bright) : 0.0;
    if (approx_form3a < 0.0) approx_form3a = 0.0;
    
    double[] ev1 = evaluateMetrics(1, z1_pts, max_bright, form1a, 0.0, 0.0, 0.0);
    double[] ev2 = evaluateMetrics(2, z2_pts, max_bright, approx_form2a, best_b, form2c, z1e);
    z1_nrmse = ev1[1]; z1_bias = ev1[2]; z2_nrmse = ev2[1]; z2_bias = ev2[2];
    
    if (z3_pts.size() >= 2) {
        double[] ev3 = evaluateMetrics(3, z3_pts, max_bright, approx_form3a, 0.0, 0.0, 0.0);
        z3_nrmse = ev3[1]; z3_bias = ev3[2];
    } else { z3_nrmse = 0.0; z3_bias = 0.0; }
    
    double size_p = calculateSizePenalty(z1_pts.size()) + calculateSizePenalty(z2_pts.size()) + calculateSizePenalty(z3_pts.size());
    double reg_p = 0.001 * best_b * best_b + 0.0005 * Math.abs(form2c);

    return (50.0 * (z1_nrmse + z2_nrmse + z3_nrmse)) + 
           (Math.abs(z1_bias) + Math.abs(z2_bias) + Math.abs(z3_bias)) + 
           size_p + reg_p;
}

/* Helper: Full fit and cost calculation */
double[] calculateFitAndCost(double z1e, double z2e, List dataPoints, int N, double current_form2b, double current_form2c, double max_bright) {
    double[] results = new double[18];
    int i, j, iter, max_iter, valid_z2_pts;
    double cand_form2a, cand_form2b, cand_form2c, cand_form2d, cand_r2_z2, MB;
    double cand_form1a, cand_r2_z1, b, c, form2d;
    double x, y_actual, term_x, term_d, y_predicted, error, d_term_c, base, base_d;
    double boundary_y2_end, cand_form3a, cand_r2_z3, w;
    double z1_nrmse, z1_bias, z2_nrmse, z2_bias, z3_nrmse, z3_bias, size_penalty, cost, r2_penalty, reg_penalty;
    double[] fitZ1_final, pt;
    double sum_num, sum_den, err_c, lr_c, sum_w2;
    List z1_pts, z2_pts, z3_pts;
    double prev_c;

    for (i=0; i<18; i++) results[i] = 0.0;
    results[0] = 1e12; 

    if (z1e >= z2e || z1e < 0) return results;

    z1_pts = new ArrayList(N); z2_pts = new ArrayList(N); z3_pts = new ArrayList(N);
    for (i = 0; i < N; i++) {
        pt = (double[]) dataPoints.get(i); 
        if (pt[0] <= z1e) z1_pts.add(pt);
        else if (pt[0] <= z2e) z2_pts.add(pt);
        else z3_pts.add(pt);
    }
    
    if (z1_pts.size() < 3 || z2_pts.size() < 3) return results;

    fitZ1_final = fitZone1(z1_pts); cand_form1a = fitZ1_final[0]; cand_r2_z1 = fitZ1_final[1];
    
    b = current_form2b; c = current_form2c; if (c < -50.0) c = -50.0;
    cand_form2a = cand_form1a * Math.sqrt(z1e); form2d = z1e;

    max_iter = 100; lr_c = 0.2; prev_c = c;
    
    for (iter = 0; iter < max_iter; iter++) {
        sum_num = 0.0; sum_den = 0.0; valid_z2_pts = 0; sum_w2 = 0.0;
        base_d = form2d - c; if (base_d < 0.5) base_d = 0.5;
        term_d = safePowDelta(base_d, 0.33);
        
        for (i = 0; i < z2_pts.size(); i++) {
            pt = (double[]) z2_pts.get(i); w = getLogWeight(pt);
            if (pt[0] <= c || form2d <= c) continue;
            term_x = safePowDelta(pt[0] - c, 0.33) - term_d;
            sum_num += w * (pt[1] - cand_form2a) * term_x; sum_den += w * term_x * term_x;
            valid_z2_pts++; sum_w2 += w;
        }
        
        if (valid_z2_pts < 3) break;
        if (sum_den > 1e-9) b = sum_num / sum_den;
        if (b < 0.0) b = 0.01;

        err_c = 0.0;
        for (i = 0; i < z2_pts.size(); i++) {
            pt = (double[]) z2_pts.get(i); x = pt[0]; y_actual = pt[1]; w = getLogWeight(pt);
            if (x <= c || form2d <= c) continue;
            term_x = safePowDelta(x - c, 0.33); 
            y_predicted = cand_form2a + b * (term_x - term_d);
            error = y_predicted - y_actual;
            base = x - c; if (base < 0.5) base = 0.5;
            
            d_term_c = (0.33 * b) * (Math.pow(base_d, -0.67) - Math.pow(base, -0.67));
            err_c += w * error * d_term_c;
        }
        if (sum_w2 > 1e-9) c -= (lr_c * err_c) / sum_w2;
        c = Math.min(c, form2d - 0.5); if (c < -50.0) c = -50.0;
        if (Math.abs(c - prev_c) < 0.002) break;
        prev_c = c; lr_c *= 0.95;
    }

    cand_form2b = b; cand_form2c = c; cand_form2d = form2d;
    cand_r2_z2 = getR2_Z2_only(z2_pts, cand_form2c, cand_form2d, cand_form2a, cand_form2b);

    MB = max_bright; 
    boundary_y2_end = cand_form2a + cand_form2b * (safePowDelta(z2e - cand_form2c, 0.33) - safePowDelta(z1e - cand_form2c, 0.33));
    cand_form3a = 0.0; 
    if (MB > 0.01) cand_form3a = z2e * (MB - boundary_y2_end) / MB;
    if (cand_form3a < 0.0) cand_form3a = 0.0;

    z1_nrmse = 0.0; z1_bias = 0.0; z2_nrmse = 0.0; z2_bias = 0.0; z3_nrmse = 0.0; z3_bias = 0.0; cand_r2_z3 = -2.0;
    
    if (z1_pts.size() > 0) {
        double[] ev1 = evaluateMetrics(1, z1_pts, max_bright, cand_form1a, 0.0, 0.0, 0.0);
        z1_nrmse = ev1[1]; z1_bias = ev1[2];
    }
    if (z2_pts.size() > 0) {
        double[] ev2 = evaluateMetrics(2, z2_pts, max_bright, cand_form2a, cand_form2b, cand_form2c, z1e);
        z2_nrmse = ev2[1]; z2_bias = ev2[2];
    }
    if (z3_pts.size() > 0) {
        double[] ev3 = evaluateMetrics(3, z3_pts, max_bright, cand_form3a, 0.0, 0.0, 0.0);
        cand_r2_z3 = ev3[0]; z3_nrmse = ev3[1]; z3_bias = ev3[2];
    }
    
    size_penalty = calculateSizePenalty(z1_pts.size()) + calculateSizePenalty(z2_pts.size()) + calculateSizePenalty(z3_pts.size());
    reg_penalty = 0.001 * cand_form2b * cand_form2b + 0.0005 * Math.abs(cand_form2c);
    
    double z3_weight = 1.0; r2_penalty = 0.0;
    if (cand_r2_z3 > -2.0) {
        if (cand_r2_z3 < 0.65) { z3_weight = 0.5; r2_penalty += 0.5 * (0.65 - cand_r2_z3); }
    } else { if (N > 12) size_penalty += 0.5; }

    if (cand_r2_z2 > -2.0 && cand_r2_z2 < 0.65) {
        r2_penalty += 3.0 * (0.65 - cand_r2_z2); 
    }
    
    if (boundary_y2_end > max_bright) {
        cost = 1e6;
    } else {
        cost = (50.0 * (z1_nrmse + z2_nrmse + z3_weight*z3_nrmse)) + 
               (Math.abs(z1_bias) + Math.abs(z2_bias) + Math.abs(z3_bias)) + 
               size_penalty + reg_penalty + r2_penalty;
    }

    results[0] = cost; results[1] = z1e; results[2] = z2e; results[3] = cand_form1a;
    results[4] = cand_form2a; results[5] = cand_form2b; results[6] = cand_form2c;
    results[7] = cand_form2d; results[8] = cand_form3a; results[9] = cand_r2_z1;
    results[10] = cand_r2_z2; results[11] = cand_r2_z3; results[12] = z1_nrmse;
    results[13] = z2_nrmse; results[14] = z3_nrmse; results[15] = z1_bias;
    results[16] = z2_bias; results[17] = z3_bias;

    return results;
}

/* ======================= MAIN ======================= */
try {
    int arraySize = 0, N, TOP_K_Z1, i, k, t, R, j, pt_idx, i_z1, i_z2;
    double current_form1a, current_form2a, current_form2b, current_form2c, current_zone1end, current_zone2end, max_bright, current_lux;
    double[] firstPt, lastPt, z1_scores, z1_values, fitZ1, fitZ3;
    double tmpS, tmpV, combinedScore, r2_z1_temp, r2_z2_temp, temp_form2d;
    double global_bestCost = 1e12, best_z1_end, best_z2_end, best_form1a, best_form2a, best_form2b;
    double best_form2c, best_form2d, best_form3a, best_r2_z1 = 0.0, best_r2_z2 = 0.0, best_r2_z3 = 0.0;
    double best_z1_nrmse = 0.0, best_z2_nrmse = 0.0, best_z3_nrmse = 0.0, best_z1_bias = 0.0, best_z2_bias = 0.0, best_z3_bias = 0.0;
    double cand_z1_end, cand_z2_end;
    double lux_before_z1, lux_after_z1, refined_z1_end, refined_z2_end;
    double k_bestCost, EARLY_STOP_COST;
    double[] pt, z1_refine_results, z2_refine_results, k_best_results;
    List dataPoints, remainingPoints, temp_zone1, temp_zone2, z1_candidates, z2_candidates;
    double best_approx_cost_z1, best_approx_z1e, approx_cost, best_approx_cost_z2, best_approx_z2e;
    int hop_count, MAX_HOPS; boolean hopped;
    double hop_threshold;
    String[] parts;
    String z1_r2_str, z2_r2_str, z3_r2_str, z1ErrQual, z2ErrQual, z3ErrQual, z1BiasDir, z2BiasDir, z3BiasDir;
    String z1BiasQual, z2BiasQual, z3BiasQual, z1ShapeQual, z2ShapeQual, z3ShapeQual, overallQual;
    String overallLine, z1Line, z2Line, z3Line;
    double z1_err_pct, z2_err_pct, z3_err_pct, avgErr, absB1, absB2, absB3;
    int iter_pass; 
    int idx_75, idx_90, step;
    double test_z2e_75, test_z2e_90, cost_75, cost_90, best_z2_init_cost, test_z2e, test_cost;
    int current_z2_idx, start_idx, end_idx;
    double z2_search_min, z2_search_max;
    List final_z1, final_z2, final_z3;
    double[] fz1, fz2, fz3;
    
    logBuffer.append("--- Engine V43.8 (Confidence Fix) ---\n");
    current_form1a = Double.parseDouble(tasker.getVariable("AAB_Form1A"));
    current_form2a = Double.parseDouble(tasker.getVariable("AAB_Form2A"));
    current_form2b = Double.parseDouble(tasker.getVariable("AAB_Form2B"));
    current_form2c = Double.parseDouble(tasker.getVariable("AAB_Form2C"));
    if (current_form2c < -50.0) current_form2c = -50.0;
    current_zone1end = Double.parseDouble(tasker.getVariable("AAB_Zone1End"));
    current_zone2end = Double.parseDouble(tasker.getVariable("AAB_Zone2End"));
    max_bright       = Double.parseDouble(tasker.getVariable("AAB_MaxBright"));
    
    logBuffer.append("[Input Parameters]\n");
    logBuffer.append(String.format("  Form1a (current): %.3f\n", new Object[]{new Double(current_form1a)}));
    logBuffer.append(String.format("  Form2a (current): %.3f\n", new Object[]{new Double(current_form2a)}));
    logBuffer.append(String.format("  Form2b (current): %.3f\n", new Object[]{new Double(current_form2b)}));
    logBuffer.append(String.format("  Form2c (current): %.3f\n", new Object[]{new Double(current_form2c)}));
    logBuffer.append(String.format("  Zone1End (current): %.1f\n", new Object[]{new Double(current_zone1end)}));
    logBuffer.append(String.format("  Zone2End (current): %.1f\n", new Object[]{new Double(current_zone2end)}));
    logBuffer.append(String.format("  MaxBright: %.1f\n\n", new Object[]{new Double(max_bright)}));

    dataPoints = new ArrayList();
    int idx = 1;
    int emptyCount = 0; 
    
    while (idx < 1000) { 
        String val = tasker.getVariable("AAB_Overrides" + idx);
        
        if (val == null) {
            if (emptyCount > 2) break; 
            emptyCount++;
            idx++;
            continue;
        }
        emptyCount = 0; 
        
        try {
            parts = val.split(",");
            if (parts.length >= 2) {
                double[] newPt = new double[4];
                String p0 = parts[0].trim();
                String p1 = parts[1].trim();
                
                if (p0.length() > 0 && p1.length() > 0) {
                    newPt[0] = Double.parseDouble(p0); 
                    double rawVal = Double.parseDouble(p1);
                    newPt[1] = Math.max(0.0, Math.min(rawVal, max_bright));
                    
                    if (parts.length == 2) {
                        newPt[2] = 1.0; newPt[3] = 1.0; 
                    } else if (parts.length == 3) {
                        String p2 = parts[2].trim();
                        newPt[2] = (p2.length() > 0) ? Double.parseDouble(p2) : 1.0;
                        newPt[3] = 2.0; 
                    } else {
                        String p2 = parts[2].trim();
                        String p3 = parts[3].trim();
                        newPt[2] = (p2.length() > 0) ? Double.parseDouble(p2) : 1.0;
                        newPt[3] = (p3.length() > 0) ? Double.parseDouble(p3) : 1.0;
                    }
                    dataPoints.add(newPt);
                }
            }
        } catch (Exception parseEx) {}
        idx++;
    }

    Collections.sort(dataPoints, new Comparator() {
        public int compare(Object o1, Object o2) {
            double[] p1 = (double[]) o1; 
            double[] p2 = (double[]) o2;
            return Double.compare(p1[0], p2[0]);
        }
    });

    int realCount = 0;
    boolean[] binsFilled = new boolean[]{false, false, false, false, false};
    
    for (i=0; i<dataPoints.size(); i++) {
        pt = (double[]) dataPoints.get(i);
        if (isRealDataPoint(pt)) {
            realCount++;
            double lx = pt[0];
            if (lx < 10.0) binsFilled[0] = true;
            else if (lx < 100.0) binsFilled[1] = true;
            else if (lx < 1000.0) binsFilled[2] = true;
            else if (lx < 10000.0) binsFilled[3] = true;
            else binsFilled[4] = true;
        }
    }

    double ghostWeight = 0.1 + (0.4 * (realCount / 50.0));
    if (ghostWeight > 0.5) ghostWeight = 0.5;

    double cur_term_d = safePowDelta(current_zone1end - current_form2c, 0.33);
    double cur_y_z2 = current_form2a + current_form2b * (safePowDelta(current_zone2end - current_form2c, 0.33) - cur_term_d);
    double cur_form3a = 0.0;
    if (max_bright > 0.01) cur_form3a = current_zone2end * (max_bright - cur_y_z2) / max_bright;
    if (cur_form3a < 0.0) cur_form3a = 0.0;
    
    double[] ghostLuxes = {3.0, 31.0, 316.0, 3162.0, 15000.0};
    boolean ghostsAdded = false;
    
    for (i=0; i<5; i++) {
        if (!binsFilled[i]) {
            double gLux = ghostLuxes[i];
            double gBright = 0.0;
            if (gLux <= current_zone1end) {
                String f1aStr = tasker.getVariable("AAB_Form1A"); double f1a = 0.0;
                if (f1aStr != null && f1aStr.length() > 0) { try { f1a = Double.parseDouble(f1aStr); } catch(Exception e){} }
                if (f1a <= 1e-9 && dataPoints.size() > 0) {
                     double[] fpt = (double[]) dataPoints.get(0);
                     if (fpt[0] > 1e-9) f1a = fpt[1] / Math.sqrt(fpt[0]);
                }
                gBright = f1a * Math.sqrt(gLux);
            } else if (gLux <= current_zone2end) {
                gBright = current_form2a + current_form2b * (safePowDelta(gLux - current_form2c, 0.33) - cur_term_d);
            } else {
                gBright = max_bright - (cur_form3a / gLux) * max_bright;
            }
            if (gBright < 0.0) gBright = 0.0; if (gBright > max_bright) gBright = max_bright;
            dataPoints.add(new double[]{gLux, gBright, ghostWeight, 3.0});
            ghostsAdded = true;
            logBuffer.append("  + Ghost Point: " + gLux + " lux -> " + Math.round(gBright) + " br (Bin " + (i+1) + " empty)\n");
        }
    }

    if (ghostsAdded) {
        Collections.sort(dataPoints, new Comparator() {
            public int compare(Object o1, Object o2) {
                double[] p1 = (double[]) o1; double[] p2 = (double[]) o2; return Double.compare(p1[0], p2[0]);
            }
        });
    }
    
    double sumRaw = 0.0; int cntRaw = 0;
    for (i=0; i<dataPoints.size(); i++) {
        pt = (double[]) dataPoints.get(i);
        if (isRealDataPoint(pt)) { sumRaw += pt[2]; cntRaw++; }
    }
    double meanRaw = (cntRaw > 0) ? (sumRaw / cntRaw) : 1.0;
    double globalScale = 1.0 / Math.max(meanRaw, 1e-9);

    for (i=0; i<dataPoints.size(); i++) { pt = (double[]) dataPoints.get(i); pt[2] = pt[2] * globalScale; }
    logBuffer.append(String.format("Global Weight Norm: Scale=%.4f (Mean Raw=%.4f)\n\n", new Object[]{new Double(globalScale), new Double(meanRaw)}));

    if (dataPoints.size() < 9) {
        logBuffer.append("Not enough data points (" + dataPoints.size() + "). Aborting.\n");
        tasker.setVariable("AAB_Test", logBuffer.toString()); return;
    }

    firstPt = (double[]) dataPoints.get(0); lastPt = (double[]) dataPoints.get(dataPoints.size() - 1);
    logBuffer.append("[Input Data] " + dataPoints.size() + " points from " + firstPt[0] + " to " + lastPt[0] + " lux.\n\n");

    N = dataPoints.size();
    
    double[] current_fit = calculateFitAndCost(current_zone1end, current_zone2end, dataPoints, N, current_form2b, current_form2c, max_bright);
    
    if (current_fit[0] < 1e11) {
        global_bestCost = current_fit[0];
        best_z1_end = current_zone1end; best_z2_end = current_zone2end;
        best_form1a = current_fit[3]; best_form2a = current_fit[4]; best_form2b = current_fit[5];
        best_form2c = current_fit[6]; best_form2d = current_fit[7]; best_form3a = current_fit[8];
        best_r2_z1 = current_fit[9]; best_r2_z2 = current_fit[10]; best_r2_z3 = current_fit[11];
        best_z1_nrmse = current_fit[12]; best_z2_nrmse = current_fit[13]; best_z3_nrmse = current_fit[14];
        best_z1_bias = current_fit[15]; best_z2_bias = current_fit[16]; best_z3_bias = current_fit[17];
        suggestion_made = true; 
        logBuffer.append(String.format("Current Benchmark Valid: Cost=%.5f (Z1e=%.1f, Z2e=%.1f)\n\n", new Object[]{new Double(global_bestCost), new Double(best_z1_end), new Double(best_z2_end)}));
    } else {
        global_bestCost = 1e12; suggestion_made = false;
        logBuffer.append("Current Benchmark INVALID. Optimizer forced to find new layout.\n\n");
    }

    TOP_K_Z1 = Math.min(5, N - 8); if (TOP_K_Z1 < 1) TOP_K_Z1 = 1;
    EARLY_STOP_COST = 3.0; MAX_HOPS = Math.min(8, Math.max(3, (int)(N / 5)));

    logBuffer.append("--- Stage 1: Searching for Top " + TOP_K_Z1 + " Zone1End Candidates ---\n");
    z1_scores = new double[TOP_K_Z1]; z1_values = new double[TOP_K_Z1];
    for (i = 0; i < TOP_K_Z1; i++) { z1_scores[i] = -9999.0; z1_values[i] = current_zone1end; }

    for (i = 2; i < N - 6; i++) {
        temp_zone1 = dataPoints.subList(0, i + 1); temp_zone2 = dataPoints.subList(i + 1, N);
        if (temp_zone1.size() < 3 || temp_zone2.size() < 3) continue;
        fitZ1 = fitZone1(temp_zone1); r2_z1_temp = fitZ1[1];
        temp_form2d = ((double[]) temp_zone1.get(temp_zone1.size() - 1))[0];
        r2_z2_temp = getR2_Z2_only(temp_zone2, current_form2c, temp_form2d, current_form2a, current_form2b);
        
        double penalty = (temp_zone1.size() < 4) ? 0.2 : 0.0;
        combinedScore = r2_z1_temp + r2_z2_temp - penalty;
        
        int dup_idx = -1;
        for (int d = 0; d < TOP_K_Z1; d++) {
            if (z1_scores[d] > -9998.0 && Math.abs(z1_values[d] - temp_form2d) < 1e-9) {
                dup_idx = d; break;
            }
        }
        
        if (dup_idx != -1) {
            // Duplicate boundary found: Update if the new split has a better score
            if (combinedScore > z1_scores[dup_idx]) {
                z1_scores[dup_idx] = combinedScore;
                // Bubble up to maintain sort
                for (t = dup_idx; t > 0; t--) {
                    if (z1_scores[t] > z1_scores[t - 1]) {
                        tmpS = z1_scores[t - 1]; tmpV = z1_values[t - 1];
                        z1_scores[t - 1] = z1_scores[t]; z1_values[t - 1] = z1_values[t];
                        z1_scores[t] = tmpS; z1_values[t] = tmpV;
                    }
                }
            }
        } else if (combinedScore > z1_scores[TOP_K_Z1 - 1]) {
            // New boundary candidate: Insert at bottom and bubble up
            z1_scores[TOP_K_Z1 - 1] = combinedScore; z1_values[TOP_K_Z1 - 1] = temp_form2d;
            for (t = TOP_K_Z1 - 1; t > 0; t--) {
                if (z1_scores[t] > z1_scores[t - 1]) {
                    tmpS = z1_scores[t - 1]; tmpV = z1_values[t - 1];
                    z1_scores[t - 1] = z1_scores[t]; z1_values[t - 1] = z1_values[t];
                    z1_scores[t] = tmpS; z1_values[t] = tmpV;
                }
            }
        }
    }
    for (i = 0; i < TOP_K_Z1; i++) logBuffer.append(String.format("  Top Cand #%d: Z1End=%.2f (Score: %.3f)\n", new Object[]{new Integer(i+1), new Double(z1_values[i]), new Double(z1_scores[i])}));

    logBuffer.append("\n--- Stage 2 & 3: Global Search & Coordinate Descent ---\n");
    for (k = 0; k < TOP_K_Z1; k++) {
        if (k > 0 && global_bestCost < EARLY_STOP_COST) break;
        if (z1_scores[k] <= -9998.0) continue;
        
        cand_z1_end = z1_values[k]; remainingPoints = new ArrayList();
        for (i = 0; i < N; i++) { pt = (double[]) dataPoints.get(i); if (pt[0] > cand_z1_end) remainingPoints.add(pt); }
        if (remainingPoints.size() < 4) continue;

        best_z2_init_cost = 1e12; cand_z2_end = current_zone2end;
        if (remainingPoints.size() > 4) {
            idx_75 = Math.min((int)(remainingPoints.size() * 0.75), remainingPoints.size() - 2);
            idx_90 = Math.min((int)(remainingPoints.size() * 0.90), remainingPoints.size() - 2);
            test_z2e_75 = ((double[]) remainingPoints.get(idx_75))[0];
            test_z2e_90 = ((double[]) remainingPoints.get(idx_90))[0];
            cost_75 = approximateCost(cand_z1_end, test_z2e_75, dataPoints, N, current_form1a, current_form2b, current_form2c, max_bright);
            cost_90 = approximateCost(cand_z1_end, test_z2e_90, dataPoints, N, current_form1a, current_form2b, current_form2c, max_bright);
            if (cost_75 < best_z2_init_cost) { best_z2_init_cost = cost_75; cand_z2_end = test_z2e_75; }
            if (cost_90 < best_z2_init_cost) { best_z2_init_cost = cost_90; cand_z2_end = test_z2e_90; }
        }

        step = Math.max(1, remainingPoints.size() / 10);
        for (j = 1; j < remainingPoints.size() - 2; j += step) {
            test_z2e = ((double[]) remainingPoints.get(j))[0];
            if (test_z2e <= cand_z1_end + 2.0) continue;
            test_cost = approximateCost(cand_z1_end, test_z2e, dataPoints, N, current_form1a, current_form2b, current_form2c, max_bright);
            if (test_cost < best_z2_init_cost) { best_z2_init_cost = test_cost; cand_z2_end = test_z2e; }
        }

        logBuffer.append(String.format(" -> Cand(k=%d): Z1e=%.2f, Initial Z2e Split=%.1f\n", new Object[]{new Integer(k), new Double(cand_z1_end), new Double(cand_z2_end)}));
        
        k_best_results = calculateFitAndCost(cand_z1_end, cand_z2_end, dataPoints, N, current_form2b, current_form2c, max_bright);
        if (k_best_results[0] > 1e11) continue;
        k_bestCost = k_best_results[0];

        if (k_bestCost > 1e5) {
            logBuffer.append(String.format("    ! Skipped: boundary violation (cost=%.0f, likely Z2End > MaxBright)\n", new Object[]{new Double(k_bestCost)}));
            continue;
        }
        
        for (iter_pass = 0; iter_pass < 3; iter_pass++) {
            hop_count = 0; hopped = true;
            while (hopped && hop_count < MAX_HOPS && k_best_results[12] > 0.015) {
                hopped = false;
                lux_before_z1 = ((double[]) dataPoints.get(0))[0]; lux_after_z1 = ((double[]) dataPoints.get(dataPoints.size() - 1))[0];
                for(pt_idx = 1; pt_idx < dataPoints.size(); pt_idx++) {
                    current_lux = ((double[]) dataPoints.get(pt_idx))[0];
                    if (current_lux >= k_best_results[1]) {
                        lux_before_z1 = ((double[]) dataPoints.get(pt_idx - 1))[0];
                        if (pt_idx < dataPoints.size() - 1) lux_after_z1 = ((double[]) dataPoints.get(pt_idx + 1))[0]; else lux_after_z1 = current_lux;
                        break;
                    }
                }
                z1_candidates = generateSmartRefinementPoints(lux_before_z1, lux_after_z1, k_best_results[1], calculateRefinePoints(lux_before_z1, lux_after_z1, k_best_results[1]));
                
                best_approx_cost_z1 = 1e12; best_approx_z1e = k_best_results[1];
                for (i_z1 = 0; i_z1 < z1_candidates.size(); i_z1++) {
                    refined_z1_end = ((Double)z1_candidates.get(i_z1)).doubleValue();
                    if (refined_z1_end >= k_best_results[2] - 1.0) continue;
                    approx_cost = approximateCost(refined_z1_end, k_best_results[2], dataPoints, N, k_best_results[3], k_best_results[5], k_best_results[6], max_bright);
                    if (approx_cost < best_approx_cost_z1) { best_approx_cost_z1 = approx_cost; best_approx_z1e = refined_z1_end; }
                }
                
                hop_threshold = Math.max(0.5, k_best_results[1] * 0.005);
                if (Math.abs(best_approx_z1e - k_best_results[1]) > hop_threshold) {
                    z1_refine_results = calculateFitAndCost(best_approx_z1e, k_best_results[2], dataPoints, N, k_best_results[5], k_best_results[6], max_bright);
                    if (z1_refine_results[0] < k_bestCost) {
                        k_bestCost = z1_refine_results[0]; k_best_results = z1_refine_results; hopped = true; hop_count++;
                        logBuffer.append(String.format("    [Pass %d] * Z1 Hopped to %.2f (Cost: %.4f)\n", new Object[]{new Integer(iter_pass+1), new Double(k_best_results[1]), new Double(k_bestCost)}));
                    }
                }
            }

            if (k_bestCost >= EARLY_STOP_COST) {
                current_z2_idx = 0;
                for(int m=0; m<dataPoints.size(); m++) { if (((double[])dataPoints.get(m))[0] >= k_best_results[2]) { current_z2_idx = m; break; } }
                start_idx = Math.max(0, current_z2_idx - 3); end_idx = Math.min(dataPoints.size() - 1, current_z2_idx + 5);
                z2_search_min = ((double[])dataPoints.get(start_idx))[0]; z2_search_max = ((double[])dataPoints.get(end_idx))[0];
                z2_candidates = generateSmartRefinementPoints(z2_search_min, z2_search_max, k_best_results[2], 8);
                
                best_approx_cost_z2 = 1e12; best_approx_z2e = k_best_results[2];
                for (i_z2 = 0; i_z2 < z2_candidates.size(); i_z2++) {
                    refined_z2_end = ((Double)z2_candidates.get(i_z2)).doubleValue();
                    if (refined_z2_end <= k_best_results[1] + 5.0) continue;
                    approx_cost = approximateCost(k_best_results[1], refined_z2_end, dataPoints, N, k_best_results[3], k_best_results[5], k_best_results[6], max_bright);
                    if (approx_cost < best_approx_cost_z2) { best_approx_cost_z2 = approx_cost; best_approx_z2e = refined_z2_end; }
                }
                
                if (Math.abs(best_approx_z2e - k_best_results[2]) > 1.0) {
                    z2_refine_results = calculateFitAndCost(k_best_results[1], best_approx_z2e, dataPoints, N, k_best_results[5], k_best_results[6], max_bright);
                    if(z2_refine_results[0] < k_bestCost) { 
                        k_bestCost = z2_refine_results[0]; k_best_results = z2_refine_results; 
                        logBuffer.append(String.format("    * Z2 Hopped to %.2f (Cost: %.4f)\n", new Object[]{new Double(k_best_results[2]), new Double(k_bestCost)}));
                    }
                }
            }
        } 

        if (k_best_results[0] < global_bestCost) {
            global_bestCost = k_best_results[0];
            best_z1_end = k_best_results[1]; best_z2_end = k_best_results[2];
            best_form1a = k_best_results[3]; best_form2a = k_best_results[4]; best_form2b = k_best_results[5];
            best_form2c = k_best_results[6]; best_form2d = k_best_results[7]; best_form3a = k_best_results[8];
            best_r2_z1 = k_best_results[9]; best_r2_z2 = k_best_results[10]; best_r2_z3 = k_best_results[11];
            best_z1_nrmse = k_best_results[12]; best_z2_nrmse = k_best_results[13]; best_z3_nrmse = k_best_results[14];
            best_z1_bias = k_best_results[15]; best_z2_bias = k_best_results[16]; best_z3_bias = k_best_results[17];
            suggestion_made = true;
        }
    }

    if (!suggestion_made) {
        logBuffer.append("\n--- FATAL: No valid candidates found AND baseline is invalid. ---\n");
        logBuffer.append("⚠️ ABORTING. Cannot generate a safe curve.\n");
        tasker.setVariable("suggest", "error");
    } else {
        int nz1 = 0, nz2 = 0;
        for(i=0; i<dataPoints.size(); i++) {
            pt = (double[]) dataPoints.get(i);
            if (!isRealDataPoint(pt)) continue; 
            if (pt[0] <= best_z1_end) nz1++;
            else if (pt[0] <= best_z2_end) nz2++;
        }

        // Because we restored best_r2_z1/nrmse assignments, these confidence scores will now evaluate correctly!
        double n1_w = getWeightedCount(nz1, best_r2_z1, best_z1_nrmse);
        double n2_w = getWeightedCount(nz2, best_r2_z2, best_z2_nrmse);

        double tau = 4.0; String tVal = tasker.getVariable("tau");
        if (tVal != null && tVal.length() > 0) { try { tau = Double.parseDouble(tVal); } catch (Exception e) {} }
        
        double conf_z1 = 1.0 - Math.exp(-n1_w / tau);
        double conf_z2 = 1.0 - Math.exp(-n2_w / tau);
        
        logBuffer.append(String.format("\n[Inertia Blending] Confidence: Z1=%.2f, Z2=%.2f (Tau=%.1f)\n", new Object[]{new Double(conf_z1), new Double(conf_z2), new Double(tau)}));

        best_z1_end = best_z1_end * conf_z1 + current_zone1end * (1.0 - conf_z1);
        best_form1a = best_form1a * conf_z1 + current_form1a * (1.0 - conf_z1);

        best_z2_end = best_z2_end * conf_z2 + current_zone2end * (1.0 - conf_z2);
        best_form2a = best_form2a * conf_z2 + current_form2a * (1.0 - conf_z2);
        best_form2b = best_form2b * conf_z2 + current_form2b * (1.0 - conf_z2);
        best_form2c = best_form2c * conf_z2 + current_form2c * (1.0 - conf_z2);
        
        best_form2d = best_z1_end; 

        double term_d_final = safePowDelta(best_z1_end - best_form2c, 0.33);
        double term_x_final = safePowDelta(best_z2_end - best_form2c, 0.33);
        double y_boundary_z2 = best_form2a + best_form2b * (term_x_final - term_d_final);
        
        if (y_boundary_z2 > max_bright - 0.5) {
            y_boundary_z2 = max_bright - 0.5;
            double den = term_x_final - term_d_final;
            if (den > 1e-9) best_form2b = (y_boundary_z2 - best_form2a) / den;
            if (best_form2b < 0.0) best_form2b = 0.001;
        }
        
        if (max_bright > 0.01) best_form3a = best_z2_end * (max_bright - y_boundary_z2) / max_bright;
        else best_form3a = 0.0;
        
        if (best_form3a < 0.001) best_form3a = 0.001;
        
        // =================================================================
        // Recalculate ALL metrics AFTER blending/clamping for honest logs
        // This overwrites the temporary pre-blend metrics.
        // =================================================================
        final_z1 = new ArrayList(); final_z2 = new ArrayList(); final_z3 = new ArrayList();
        for (i = 0; i < dataPoints.size(); i++) {
            pt = (double[]) dataPoints.get(i);
            if (pt[0] <= best_z1_end) final_z1.add(pt);
            else if (pt[0] <= best_z2_end) final_z2.add(pt);
            else final_z3.add(pt);
        }
        fz1 = evaluateMetrics(1, final_z1, max_bright, best_form1a, 0.0, 0.0, 0.0);
        fz2 = evaluateMetrics(2, final_z2, max_bright, best_form2a, best_form2b, best_form2c, best_z1_end);
        fz3 = evaluateMetrics(3, final_z3, max_bright, best_form3a, 0.0, 0.0, 0.0);

        best_r2_z1 = fz1[0]; best_z1_nrmse = fz1[1]; best_z1_bias = fz1[2];
        best_r2_z2 = fz2[0]; best_z2_nrmse = fz2[1]; best_z2_bias = fz2[2];
        best_r2_z3 = fz3[0]; best_z3_nrmse = fz3[1]; best_z3_bias = fz3[2];
        // =================================================================

        tasker.setVariable("suggest", "true");
        tasker.setVariable("suggestion_zone1end", String.valueOf(Math.round(best_z1_end)));
        tasker.setVariable("suggestion_zone2end", String.valueOf(Math.round(best_z2_end)));
        tasker.setVariable("suggestion_form1a", String.format("%.3f", new Object[]{new Double(best_form1a)}));
        tasker.setVariable("suggestion_form2a", String.format("%.3f", new Object[]{new Double(best_form2a)}));
        tasker.setVariable("suggestion_form2b", String.format("%.3f", new Object[]{new Double(best_form2b)}));
        tasker.setVariable("suggestion_form2c", String.format("%.3f", new Object[]{new Double(best_form2c)}));
        tasker.setVariable("suggestion_form2d", String.valueOf(Math.round(best_form2d)));
        tasker.setVariable("suggestion_form3a", String.format("%.3f", new Object[]{new Double(best_form3a)}));
        
        double max_impact = 0.0; double w;
        if (dataPoints.size() > 3) {
            double base_sq_err = 0.0; double y_curve;
            double term_d_z2 = safePowDelta(best_z1_end - best_form2c, 0.33);

            for (i=0; i<dataPoints.size(); i++) {
                pt = (double[])dataPoints.get(i); w = getLogWeight(pt);
                if(pt[0] <= best_z1_end) y_curve = best_form1a * Math.sqrt(pt[0]);
                else if(pt[0] <= best_z2_end) y_curve = best_form2a + best_form2b * (safePowDelta(pt[0]-best_form2c, 0.33) - term_d_z2);
                else y_curve = max_bright - (best_form3a / pt[0]) * max_bright;
                base_sq_err += w * Math.pow(y_curve - pt[1], 2.0);
            }
            
            for (i=0; i<dataPoints.size(); i++) {
                pt = (double[])dataPoints.get(i); w = getLogWeight(pt);
                if(pt[0] <= best_z1_end) y_curve = best_form1a * Math.sqrt(pt[0]);
                else if(pt[0] <= best_z2_end) y_curve = best_form2a + best_form2b * (safePowDelta(pt[0]-best_form2c, 0.33) - term_d_z2);
                else y_curve = max_bright - (best_form3a / pt[0]) * max_bright;
                double err_contrib = w * Math.pow(y_curve - pt[1], 2.0);
                double impact = err_contrib / (base_sq_err + 1e-9);
                if(impact > max_impact) max_impact = impact;
            }
        }
        String stabilityRating = (max_impact < 0.3) ? "High" : (max_impact < 0.6) ? "Moderate" : "Low (Outlier Driven)";
        
        logBuffer.append("\n--- Final Curve Diagnostics (Post-Blend) ---\n");
        logBuffer.append("Fit Stability: " + stabilityRating + String.format(" (Max Impact: %.1f%%)\n\n", new Object[]{new Double(max_impact*100.0)}));
        logBuffer.append("[Zone Boundaries]\n");
        logBuffer.append(String.format("  Zone1End: %.2f (lux)\n", new Object[]{new Double(best_z1_end)}));
        logBuffer.append(String.format("  Zone2End: %.2f (lux)\n\n", new Object[]{new Double(best_z2_end)}));
        logBuffer.append("[Curve Parameters]\n");
        logBuffer.append(String.format("  Form1a (sqrt scale): %.4f\n", new Object[]{new Double(best_form1a)}));
        logBuffer.append(String.format("  Form2a (align): %.4f\n", new Object[]{new Double(best_form2a)}));
        logBuffer.append(String.format("  Form2b (scale): %.4f\n", new Object[]{new Double(best_form2b)}));
        logBuffer.append(String.format("  Form2c (offset): %.4f\n", new Object[]{new Double(best_form2c)}));
        logBuffer.append(String.format("  Form2d (Z1 align): %.4f\n", new Object[]{new Double(best_form2d)}));
        logBuffer.append(String.format("  Form3a (tail align): %.4f\n\n", new Object[]{new Double(best_form3a)}));
        logBuffer.append("[Goodness of Fit (R²)]\n");
        logBuffer.append(String.format("  R² Zone 1: %.3f\n", new Object[]{new Double(best_r2_z1)}));
        logBuffer.append(String.format("  R² Zone 2: %.3f\n", new Object[]{new Double(best_r2_z2)}));
        logBuffer.append(String.format("  R² Zone 3: %.3f\n\n", new Object[]{new Double(best_r2_z3)}));
        logBuffer.append("[Error Metrics]\n");
        logBuffer.append(String.format("  nRMSE Z1: %.2f%%\n", new Object[]{new Double(best_z1_nrmse * 100.0)}));
        logBuffer.append(String.format("  nRMSE Z2: %.2f%%\n", new Object[]{new Double(best_z2_nrmse * 100.0)}));
        logBuffer.append(String.format("  nRMSE Z3: %.2f%%\n", new Object[]{new Double(best_z3_nrmse * 100.0)}));
        logBuffer.append(String.format("  Bias Z1:  %.2f\n", new Object[]{new Double(best_z1_bias)}));
        logBuffer.append(String.format("  Bias Z2:  %.2f\n", new Object[]{new Double(best_z2_bias)}));
        logBuffer.append(String.format("  Bias Z3:  %.2f\n", new Object[]{new Double(best_z3_bias)}));
        logBuffer.append("--------------------------\n\n");

        z1_r2_str = (best_r2_z1 > -2.0) ? String.format("%.2f", new Object[]{new Double(best_r2_z1)}) : "N/A";
        z2_r2_str = (best_r2_z2 > -2.0) ? String.format("%.2f", new Object[]{new Double(best_r2_z2)}) : "N/A";
        z3_r2_str = (best_r2_z3 > -2.0) ? String.format("%.2f", new Object[]{new Double(best_r2_z3)}) : "N/A";

        z1_err_pct = best_z1_nrmse * 100.0; z2_err_pct = best_z2_nrmse * 100.0; z3_err_pct = best_z3_nrmse * 100.0;
        if (z1_err_pct < 1) z1ErrQual = "Excellent"; else if (z1_err_pct < 3) z1ErrQual = "Very Good"; else if (z1_err_pct < 7) z1ErrQual = "Good"; else if (z1_err_pct < 15) z1ErrQual = "Fair"; else z1ErrQual = "Poor";
        if (z2_err_pct < 1) z2ErrQual = "Excellent"; else if (z2_err_pct < 3) z2ErrQual = "Very Good"; else if (z2_err_pct < 7) z2ErrQual = "Good"; else if (z2_err_pct < 15) z2ErrQual = "Fair"; else z2ErrQual = "Poor";
        if (z3_err_pct < 1) z3ErrQual = "Excellent"; else if (z3_err_pct < 3) z3ErrQual = "Very Good"; else if (z3_err_pct < 7) z3ErrQual = "Good"; else if (z3_err_pct < 15) z3ErrQual = "Fair"; else z3ErrQual = "Poor";
        if (best_z1_bias > 0.2) z1BiasDir = "brighter"; else if (best_z1_bias < -0.2) z1BiasDir = "dimmer"; else z1BiasDir = "neutral";
        if (best_z2_bias > 0.2) z2BiasDir = "brighter"; else if (best_z2_bias < -0.2) z2BiasDir = "dimmer"; else z2BiasDir = "neutral";
        if (best_z3_bias > 0.2) z3BiasDir = "brighter"; else if (best_z3_bias < -0.2) z3BiasDir = "dimmer"; else z3BiasDir = "neutral";
        absB1 = Math.abs(best_z1_bias); absB2 = Math.abs(best_z2_bias); absB3 = Math.abs(best_z3_bias);
        if (absB1 < 0.5) z1BiasQual = "Minimal"; else if (absB1 < 2) z1BiasQual = "Slight"; else if (absB1 < 5) z1BiasQual = "Moderate"; else z1BiasQual = "Strong";
        if (absB2 < 0.5) z2BiasQual = "Minimal"; else if (absB2 < 2) z2BiasQual = "Slight"; else if (absB2 < 5) z2BiasQual = "Moderate"; else z2BiasQual = "Strong";
        if (absB3 < 0.5) z3BiasQual = "Minimal"; else if (absB3 < 2) z3BiasQual = "Slight"; else if (absB3 < 5) z3BiasQual = "Moderate"; else z3BiasQual = "Strong";
        
        if (best_r2_z1 >= 0.9) z1ShapeQual = "Excellent"; else if (best_r2_z1 >= 0.75) z1ShapeQual = "Good"; else if (best_r2_z1 >= 0.4) z1ShapeQual = "Moderate"; else z1ShapeQual = "Poor";
        if (best_r2_z2 >= 0.9) z2ShapeQual = "Excellent"; else if (best_r2_z2 >= 0.75) z2ShapeQual = "Good"; else if (best_r2_z2 >= 0.4) z2ShapeQual = "Moderate"; else z2ShapeQual = "Poor";
        if (best_r2_z3 >= 0.9) z3ShapeQual = "Excellent"; else if (best_r2_z3 >= 0.75) z3ShapeQual = "Good"; else if (best_r2_z3 >= 0.4) z3ShapeQual = "Moderate"; else z3ShapeQual = "Poor";

        avgErr = (z1_err_pct + z2_err_pct + z3_err_pct) / 3.0;
        if (avgErr < 2) overallQual = "Excellent"; else if (avgErr < 4) overallQual = "Very Good"; else if (avgErr < 8) overallQual = "Good"; else if (avgErr < 15) overallQual = "Fair"; else overallQual = "Poor";

        overallLine = "🏆 Overall Fit: " + overallQual;
        z1Line = String.format("⚫ Dark: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", new Object[]{z1ErrQual, new Double(z1_err_pct), z1ShapeQual, z1_r2_str, z1BiasQual, z1BiasDir, new Double(best_z1_bias)});
        z2Line = String.format("⚪ Dim: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", new Object[]{z2ErrQual, new Double(z2_err_pct), z2ShapeQual, z2_r2_str, z2BiasQual, z2BiasDir, new Double(best_z2_bias)});
        z3Line = String.format("☀️ Bright: %s (%.1f%%) | Shape: %s (R²: %s) | Bias: %s %s (%.1f)", new Object[]{z3ErrQual, new Double(z3_err_pct), z3ShapeQual, z3_r2_str, z3BiasQual, z3BiasDir, new Double(best_z3_bias)});

        tasker.setVariable("suggest_r2_1", overallLine);
        tasker.setVariable("suggest_r2_2", z1Line);
        tasker.setVariable("suggest_r2_3", z2Line);
        tasker.setVariable("suggest_r2_4", z3Line);
        
        logBuffer.append("[Human Summary]\n" + overallLine + "\n" + z1Line + "\n" + z2Line + "\n" + z3Line + "\n");
    }
} catch (Exception e) {
    logBuffer.append("CRITICAL ERROR: " + e.toString() + "\n");
    e.printStackTrace();
} finally {
    logBuffer.append("\n--- Analysis Engine Finished ---\n");
    tasker.setVariable("AAB_Test", logBuffer.toString());
}
