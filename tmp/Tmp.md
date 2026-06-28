This folder contains updated Tasker scenes, profiles and tasks. Please delete after porting them over. 

Core changes in Tasker: 

* Removed significant motion event from the Panic (reset) profile and replaced it by %AAB_Proximity !~ near as a condition.
* Added a new variable, %AAB_PanicSensitivity to the Debug scene, accessible through a slider at the bottom which passes %par1 to the task _SetPanicSensitivity.
* _PanicButton task is now gated for 10 seconds by Java code, if no significant shake (depending on %AAB_PanicSensitivity) occurs in that time window, the profile won't trigger again until the phone is flipped straight and then upside-down again.
