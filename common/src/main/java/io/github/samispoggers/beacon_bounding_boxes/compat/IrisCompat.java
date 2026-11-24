package io.github.samispoggers.beacon_bounding_boxes.compat;

import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class IrisCompat {
    private static volatile boolean initialized = false;
    private static MethodHandle getInstanceHandle;
    private static MethodHandle isRenderingShadowPassHandle;
    private static MethodHandle isShaderPackInUseHandle;

    private IrisCompat() {
    }

    public static boolean isRenderingShadowPass() {
        return invokeBooleanApi(isRenderingShadowPassHandle, "shadow-pass");
    }

    public static boolean isShaderPackInUse() {
        return invokeBooleanApi(isShaderPackInUseHandle, "shader-pack");
    }

    private static boolean invokeBooleanApi(MethodHandle handle, String checkName) {
        if (!initialized) {
            init();
        }
        if (getInstanceHandle == null || handle == null) {
            return false;
        }
        try {
            Object api = getInstanceHandle.invoke();
            if (api == null) {
                return false;
            }
            return (boolean) handle.invoke(api);
        } catch (Throwable t) {
            BeaconBoundingBoxes.LOGGER.warn("Disabling Iris {} checks after unexpected failure", checkName, t);
            getInstanceHandle = null;
            isRenderingShadowPassHandle = null;
            isShaderPackInUseHandle = null;
            return false;
        }
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            if (Services.get().isModLoaded("iris")) {
                Class<?> irisApiClass = Class.forName(
                        "net.irisshaders.iris.api.v0.IrisApi",
                        false,
                        IrisCompat.class.getClassLoader()
                );
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                getInstanceHandle = lookup.findStatic(
                        irisApiClass,
                        "getInstance",
                        MethodType.methodType(irisApiClass)
                );
                isRenderingShadowPassHandle = lookup.findVirtual(
                        irisApiClass,
                        "isRenderingShadowPass",
                        MethodType.methodType(boolean.class)
                );
                isShaderPackInUseHandle = lookup.findVirtual(
                        irisApiClass,
                        "isShaderPackInUse",
                        MethodType.methodType(boolean.class)
                );
            }
        } catch (Throwable t) {
            BeaconBoundingBoxes.LOGGER.debug("Iris API not available; skipping Iris shader hooks", t);
            getInstanceHandle = null;
            isRenderingShadowPassHandle = null;
            isShaderPackInUseHandle = null;
        } finally {
            initialized = true;
        }
    }
}
