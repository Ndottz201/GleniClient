package glen.euphoria.utils;

import glen.euphoria.EuphoriaClient;

import java.util.Timer;
import java.util.TimerTask;

public class AutoSaveManager {
    private static final AutoSaveManager INSTANCE = new AutoSaveManager();
    private static final long DEBOUNCE_DELAY = 1000; // 1 second delay
    
    private Timer timer;
    private TimerTask pendingTask;
    
    private AutoSaveManager() {
        this.timer = new Timer("AutoSave-Timer", true);
    }
    
    public static AutoSaveManager getInstance() {
        return INSTANCE;
    }
    
    public void scheduleSave() {
        try {
            if (!glen.euphoria.module.modules.client.ClientSettingsModule.isAutoSaveEnabled()) {
                return;
            }
            
            if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getProfileManager() == null) {
                return;
            }
            
            if (pendingTask != null) {
                pendingTask.cancel();
            }
            
            pendingTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        EuphoriaClient.INSTANCE.getProfileManager().saveProfile("default", true);
                    } catch (Exception e) {
                    
                    }
                }
            };
            
            timer.schedule(pendingTask, DEBOUNCE_DELAY);
        } catch (Exception e) {
       
        }

    }
    
    public void shutdown() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
