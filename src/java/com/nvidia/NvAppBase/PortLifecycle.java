// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.NvAppBase;
import android.app.Activity;
import android.util.Log;
public final class PortLifecycle {
  public static void event(Activity activity,String event){
    Log.i("SMGLifecycle",event+" INSTANCE="+System.identityHashCode(activity)+" TASK="+activity.getTaskId()+" FINISHING="+activity.isFinishing());
  }
}
