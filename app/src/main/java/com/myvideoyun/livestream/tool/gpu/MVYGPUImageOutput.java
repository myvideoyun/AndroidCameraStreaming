package com.myvideoyun.livestream.tool.gpu;

import java.util.ArrayList;

public class MVYGPUImageOutput {

    private ArrayList<MVYGPUImageInput> targets = new ArrayList();

    protected ArrayList<MVYGPUImageInput> getTargets() {
        return targets;
    }

    public void addTarget(MVYGPUImageInput newTarget) {
        if (targets.contains(newTarget)) {
            return;
        }

        targets.add(newTarget);
    }

    public void removeTarget(MVYGPUImageInput targetToRemove) {
        if (!targets.contains(targetToRemove)) {
            return;
        }

        targets.remove(targetToRemove);
    }

    public void removeAllTargets() {
        targets.clear();
    }
}