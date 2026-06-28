    Profile: Panic (Reset)
    Settings: Cooldown: 15 Priority: 15
    	State: Orientation [ Is:Upside Down ]
    	State: Display State [ Is:On ]
    	State: Variable Value  [ %AAB_Proximity !~ Near ]
    
    
    
    Enter Task: _PanicButton
    
    A1: Variable Set [
         Name: %AAB_PanicSensitivity
         To: 8
         Structure Output (JSON, etc): On ]
        If  [ %AAB_PanicSensitivity !Set ]
    
    A2: Java Code [
         Code: import android.content.Context;
         import android.hardware.Sensor;
         import android.hardware.SensorEvent;
         import android.hardware.SensorEventListener;
         import android.hardware.SensorManager;
         import io.reactivex.subjects.SingleSubject;
         import java.util.concurrent.TimeUnit;
         
         /* Get and parse the sensitivity variable. */
         sensStr = tasker.getVariable("AAB_PanicSensitivity");
         sensitivity = 0;
         if (sensStr != null && !sensStr.trim().equals("")) {
             try {
                 sensitivity = Integer.parseInt(sensStr);
             } catch (Exception e) {}
         }
         
         /* Explicitly clamp the sensitivity between 0 and 10. */
         if (sensitivity < 0) {
             sensitivity = 0;
         }
         if (sensitivity > 10) {
             sensitivity = 10;
         }
         
         /* 0 means pass through immediately without veto. Return false (do not stop). */
         if (sensitivity == 0) {
             return "false";
         }
         
         /* 
          * Math adjusted for steeper difficulty at higher sensitivities:
          * At 10, threshold is 20.0 m/s^2 (requires intense >2G force) and target is 400.
          * Because of the 0.98 decay rate, if the user doesn't shake hard enough to 
          * constantly beat the decay, they physically cannot accumulate enough score to pass.
          */
         targetScore = sensitivity * 40.0;
         threshold = sensitivity * 2.0;
         
         /* Prepare signal and sensor manager. */
         resultSignal = SingleSubject.create();
         sm = context.getSystemService(Context.SENSOR_SERVICE);
         
         accel = sm.getDefaultSensor(10); /* TYPE_LINEAR_ACCELERATION */
         isLinear = true;
         if (accel == null) {
             accel = sm.getDefaultSensor(1); /* TYPE_ACCELEROMETER */
             isLinear = false;
             if (accel == null) {
                 /* If no sensor is available to measure, we must veto the action to be safe. */
                 return "true";
             }
         }
         
         /* state[0]: score, state[1-3]: gravity, state[4]: completion flag */
         state = new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
         
         listener = new SensorEventListener() {
             onSensorChanged(SensorEvent event) {
                 x = event.values[0];
                 y = event.values[1];
                 z = event.values[2];
                 
                 /* Apply a high-pass filter to strip gravity if using standard accelerometer. */
                 if (!isLinear) {
                     alpha = 0.8;
                     state[1] = alpha * state[1] + (1.0 - alpha) * x;
                     state[2] = alpha * state[2] + (1.0 - alpha) * y;
                     state[3] = alpha * state[3] + (1.0 - alpha) * z;
                     x = x - state[1];
                     y = y - state[2];
                     z = z - state[3];
                 }
                 
                 mag = Math.sqrt(x*x + y*y + z*z);
                 
                 /* 
                  * Accumulate score using a slow-leaking bucket (0.98 multiplier).
                  * We ONLY add points for the force generated ABOVE the threshold.
                  */
                 if (mag > threshold) {
                     state[0] = state[0] * 0.98 + (mag - threshold);
                 } else {
                     /* Drain the score faster (0.90) if the user pauses or stops shaking. */
                     state[0] = state[0] * 0.90;
                 }
                 
                 /* Signal success if the score requirement is met. */
                 if (state[0] >= targetScore && state[4] == 0.0) {
                     state[4] = 1.0;
                     resultSignal.onSuccess("passed");
                 }
             }
             
             onAccuracyChanged(Sensor sensor, int accuracy) {
             }
         };
         
         /* Register listener with SENSOR_DELAY_GAME (1) for fast updates (~50Hz). */
         sm.registerListener(listener, accel, 1);
         
         shouldStop = "false";
         try {
             /* Wait up to 10 seconds for the shake to complete to allow for longer efforts. */
             resultSignal.timeout(10, TimeUnit.SECONDS).blockingGet();
             /* Shake successfully reached target score, tell Tasker NOT to stop. */
             shouldStop = "false";
         } catch (Exception e) {
             /* If timeout occurs, the shake was not sufficient. Tell Tasker to stop. */
             shouldStop = "true";
         } finally {
             /* Always clean up the listener to prevent battery drain. */
             sm.unregisterListener(listener);
         }
         
         return shouldStop;
         Return: %should_stop
         Structure Output (JSON, etc): On ]
    
    A3: Stop [ ]
        If  [ %should_stop ~ true ]
    
    <This pattern is S.O.S. in morse code>
    A4: Vibrate Pattern [
         Pattern: 0,100,100,100,100,100,300,300,100,300,100,300,300,100,100,100,100,100 ]
    
    A5: Variable Set [
         Name: %AAB_Service
         To: on
         Structure Output (JSON, etc): On ]
    
    A6: Perform Task [
         Name: _QSToggleAABService V2
         Priority: %priority+3
         Structure Output (JSON, etc): On ]
    
    A7: Variable Set [
         Name: %AAB_Manual_Override
         To: true
         Structure Output (JSON, etc): On ]
    
    A8: Stop [
         Task: Smooth Brightness Transition V5 (Java) ]
    
    A9: Stop [
         Task: Smooth DC-Like Brightness Transition V5 (Java) ]
    
    A10: Display Brightness [
          Level: 255
          Disable Safeguard: On
          Ignore Current Level: On ]
    
    A11: Perform Task [
          Name: Disable Super Dimming (Unprivileged)
          Priority: %priority
          Structure Output (JSON, etc): On ]
    
    A12: Perform Task [
          Name: Disable Super Dimming (Privileged)
          Priority: %priority
          Structure Output (JSON, etc): On ]
    
    <This is redundant, but better safe than sorry!>
    A13: Destroy Scene [
          Name: AAB Color Filter
          Continue Task After Error:On ]
    
    A14: Element Visibility [
          Scene Name: AAB Brightness Settings
          Element Match: Switch
          Set: True
          Animation Time (MS): 0
          Continue Task After Error:On ]
    
    A15: Element Visibility [
          Scene Name: AAB Brightness Settings
          Element Match: Switch2
          Animation Time (MS): 0
          Continue Task After Error:On ]
    
    A16: Element Visibility [
          Scene Name: AAB Brightness Settings
          Element Match: Service_on_green
          Animation Time (MS): 0
          Continue Task After Error:On ]
    
    
