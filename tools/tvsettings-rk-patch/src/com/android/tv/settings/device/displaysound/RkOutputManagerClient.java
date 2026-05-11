package com.android.tv.settings.device.displaysound;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RkOutputManagerClient {
    static final String DESCRIPTOR = "rockchip.hardware.outputmanager@1.0::IRkOutputManager";
    private static final String INSTANCE = "default";

    private Class<?> hwBinderClass;
    private Class<?> hwParcelClass;
    private Object remote;

    static final class Mode {
        final int width;
        final int height;
        final float refreshRate;
        final int clock;
        final int flags;
        final int interlace;
        final String setName;

        Mode(int width, int height, float refreshRate, int clock, int flags, int interlace,
                String setName) {
            this.width = width;
            this.height = height;
            this.refreshRate = refreshRate;
            this.clock = clock;
            this.flags = flags;
            this.interlace = interlace;
            this.setName = setName;
        }

        String label() {
            return String.format(Locale.US, "%dx%d @ %.2f Hz%s",
                    width, height, refreshRate, interlace != 0 ? " interlaced" : "");
        }
    }

    void connect() throws Exception {
        if (remote != null) {
            return;
        }
        hwBinderClass = Class.forName("android.os.HwBinder");
        hwParcelClass = Class.forName("android.os.HwParcel");
        remote = hwBinderClass.getMethod("getService", String.class, String.class)
                .invoke(null, DESCRIPTOR, INSTANCE);
        if (remote == null) {
            throw new IllegalStateException("No " + DESCRIPTOR + "/" + INSTANCE);
        }
        callVoidNoArgs(1);
    }

    int getConnectorCount() throws Exception {
        return callDisplayInt(13, 0);
    }

    int getConnectState(long display) throws Exception {
        return callDisplayInt(14, display);
    }

    int getBuiltIn(long display) throws Exception {
        return callDisplayInt(15, display);
    }

    String getCurrentMode(long display) throws Exception {
        return callDisplayString(12, display);
    }

    String getCurrentColor(long display) throws Exception {
        return callDisplayString(11, display);
    }

    int[] getColorConfigs(long display) throws Exception {
        return callDisplayIntVector(16, display);
    }

    int[] getOverscan(long display) throws Exception {
        return callDisplayIntVector(17, display);
    }

    int[] getBcsh(long display) throws Exception {
        return callDisplayIntVector(18, display);
    }

    List<Mode> getDisplayModes(long display) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            transact(19, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            int result = ((Integer) invoke(reply, "readInt32")).intValue();
            if (result != 0) {
                throw new IllegalStateException("getDisplayModes failed: " + result);
            }
            Object header = invoke(reply, "readBuffer", Long.valueOf(16));
            int size = ((Integer) invoke(header, "getInt32", Long.valueOf(8))).intValue();
            long elemSize = 72L;
            Object child = readEmbeddedBuffer(reply, size * elemSize, header, 0L);
            ArrayList<Mode> modes = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                modes.add(parseMode(child, i * elemSize));
            }
            return modes;
        } finally {
            release(reply);
            release(request);
        }
    }

    int getHdrResolutionSupported(long display, String mode) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            invoke(request, "writeString", mode == null ? "" : mode);
            transact(28, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            int result = ((Integer) invoke(reply, "readInt32")).intValue();
            int value = ((Integer) invoke(reply, "readInt32")).intValue();
            return result == 0 ? value : -100000 - result;
        } finally {
            release(reply);
            release(request);
        }
    }

    int setMode(long display, String mode) throws Exception {
        return callDisplayStringSetter(2, display, mode);
    }

    int setColorMode(long display, String mode) throws Exception {
        return callDisplayStringSetter(10, display, mode);
    }

    int setBrightness(long display, int value) throws Exception {
        return callDisplayIntSetter(4, display, value);
    }

    int setContrast(long display, int value) throws Exception {
        return callDisplayIntSetter(5, display, value);
    }

    int setSaturation(long display, int value) throws Exception {
        return callDisplayIntSetter(6, display, value);
    }

    int setHue(long display, int value) throws Exception {
        return callDisplayIntSetter(7, display, value);
    }

    void saveConfig() throws Exception {
        callVoidNoArgs(20);
    }

    private Mode parseMode(Object blob, long off) throws Exception {
        int width = getInt32(blob, off);
        int height = getInt32(blob, off + 4);
        float refreshRate = getFloat(blob, off + 8);
        int clock = getInt32(blob, off + 12);
        int flags = getInt32(blob, off + 16);
        int interlace = getInt32(blob, off + 20);
        int hsyncStart = getInt32(blob, off + 40);
        int hsyncEnd = getInt32(blob, off + 44);
        int htotal = getInt32(blob, off + 48);
        int vsyncStart = getInt32(blob, off + 56);
        int vsyncEnd = getInt32(blob, off + 60);
        int vtotal = getInt32(blob, off + 64);
        String setName = String.format(Locale.US, "%dx%d@%.2f-%d-%d-%d-%d-%d-%d-%x-%d",
                width, height, refreshRate, hsyncStart, hsyncEnd, htotal,
                vsyncStart, vsyncEnd, vtotal, flags, clock);
        return new Mode(width, height, refreshRate, clock, flags, interlace, setName);
    }

    private int callDisplayStringSetter(int code, long display, String value) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            invoke(request, "writeString", value);
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            return ((Integer) invoke(reply, "readInt32")).intValue();
        } finally {
            release(reply);
            release(request);
        }
    }

    private int callDisplayIntSetter(int code, long display, int value) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            invoke(request, "writeInt32", Integer.valueOf(value));
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            return ((Integer) invoke(reply, "readInt32")).intValue();
        } finally {
            release(reply);
            release(request);
        }
    }

    private String callDisplayString(int code, long display) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            int result = ((Integer) invoke(reply, "readInt32")).intValue();
            String value = (String) invoke(reply, "readString");
            return result == 0 ? value : "error " + result;
        } finally {
            release(reply);
            release(request);
        }
    }

    private int callDisplayInt(int code, long display) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            int result = ((Integer) invoke(reply, "readInt32")).intValue();
            int value = ((Integer) invoke(reply, "readInt32")).intValue();
            return result == 0 ? value : -100000 - result;
        } finally {
            release(reply);
            release(request);
        }
    }

    private int[] callDisplayIntVector(int code, long display) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            invoke(request, "writeInt64", Long.valueOf(display));
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
            int result = ((Integer) invoke(reply, "readInt32")).intValue();
            int[] values = readInt32Vector(reply);
            return result == 0 ? values : new int[]{-100000 - result};
        } finally {
            release(reply);
            release(request);
        }
    }

    private void callVoidNoArgs(int code) throws Exception {
        Object request = newParcel();
        Object reply = newParcel();
        try {
            invoke(request, "writeInterfaceToken", DESCRIPTOR);
            transact(code, request, reply);
            invoke(reply, "verifySuccess");
            invoke(request, "releaseTemporaryStorage");
        } finally {
            release(reply);
            release(request);
        }
    }

    private int[] readInt32Vector(Object parcel) throws Exception {
        Object header = invoke(parcel, "readBuffer", Long.valueOf(16));
        int size = ((Integer) invoke(header, "getInt32", Long.valueOf(8))).intValue();
        Object child = readEmbeddedBuffer(parcel, size * 4L, header, 0L);
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = getInt32(child, i * 4L);
        }
        return values;
    }

    private Object newParcel() throws Exception {
        Constructor<?> ctor = hwParcelClass.getConstructor();
        return ctor.newInstance();
    }

    private Object readEmbeddedBuffer(Object parcel, long size, Object parentBlob, long offset) throws Exception {
        Object handle = invoke(parentBlob, "handle");
        return invoke(parcel, "readEmbeddedBuffer", Long.valueOf(size), handle, Long.valueOf(offset), Boolean.TRUE);
    }

    private int getInt32(Object blob, long offset) throws Exception {
        return ((Integer) invoke(blob, "getInt32", Long.valueOf(offset))).intValue();
    }

    private float getFloat(Object blob, long offset) throws Exception {
        return ((Float) invoke(blob, "getFloat", Long.valueOf(offset))).floatValue();
    }

    private Object invoke(Object target, String name, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), name, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Method findMethod(Class<?> cls, String name, Object[] args) {
        Method[] methods = cls.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < types.length; i++) {
                if (!accepts(types[i], args[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + cls.getName() + "." + name);
    }

    private boolean accepts(Class<?> type, Object arg) {
        if (arg == null) {
            return !type.isPrimitive();
        }
        if (type.isPrimitive()) {
            return (type == int.class && arg instanceof Integer)
                    || (type == long.class && arg instanceof Long)
                    || (type == boolean.class && arg instanceof Boolean);
        }
        return type.isAssignableFrom(arg.getClass());
    }

    private void transact(int code, Object request, Object reply) throws Exception {
        Method method = findMethod(remote.getClass(), "transact",
                new Object[]{Integer.valueOf(code), request, reply, Integer.valueOf(0)});
        method.setAccessible(true);
        method.invoke(remote, Integer.valueOf(code), request, reply, Integer.valueOf(0));
    }

    private void release(Object parcel) {
        try {
            invoke(parcel, "release");
        } catch (Throwable ignored) {
        }
    }
}
