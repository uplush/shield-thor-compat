// SPDX-License-Identifier: MIT
import com.nvidia.lightspeed.mediaplayer.PortSubtitle;
public class OdinClockTest {
    public static void main(String[] args) throws Exception {
        HostClock.main(args);
        if (PortSubtitle.observed == 0) throw new AssertionError("PTS observer was not called");
        System.out.println("ODIN_CLOCK_OBSERVER_CALLED=1 JNI_TESTED=0");
    }
}
