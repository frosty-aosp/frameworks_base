/*
 * Copyright (C) 2024 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ext;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivitySettingsManager;
import android.os.Process;
import android.util.Log;

import com.android.server.SystemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class MicroGPermissionService extends SystemService {

    private static final String TAG = "MicroGPermissionService";

    private static final String MICROG_APEX = "com.frosty.microg";

    private static final String GMS_CORE_PACKAGE = "com.google.android.gms";
    private static final String VENDING_PACKAGE = "com.android.vending";

    private static final String[] MICROG_PACKAGES = new String[] {
            GMS_CORE_PACKAGE,
            VENDING_PACKAGE
    };

    private static final String[] RESTRICTED_PERMISSIONS = new String[] {
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CELL_BROADCASTS",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };

    private static final String[] GMS_CORE_PERMISSIONS = new String[] {
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.GET_ACCOUNTS",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_PHONE_STATE",
            "android.permission.RECEIVE_SMS",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };

    private static final String[] VENDING_PERMISSIONS = new String[] {
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.GET_ACCOUNTS",
            "android.permission.POST_NOTIFICATIONS"
    };

    public MicroGPermissionService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        // Don't do anything if microG apex is not present.
        if (!isMicroGApexPresent()) {
            return;
        }

        Context ctx = getContext();
        if (needWhitelist(ctx)) {
            Log.i(TAG, "Setting up microG features");
            addAllUids(ctx, getUidsForPkgNames(ctx));
            setupPermissions(ctx);
        } else {
            Log.i(TAG, "Skipped setting up microG features");
        }
    }

    @SuppressLint("NewApi")
    private static void setupPermissions(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        for (String pkg : MICROG_PACKAGES) {
            for (String perm : RESTRICTED_PERMISSIONS) {
                try {
                    pm.addWhitelistedRestrictedPermission(pkg, perm,
                            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM);
                } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }

        // Recommened default permissions
        grantPermissions(pm, GMS_CORE_PACKAGE, GMS_CORE_PERMISSIONS);
        grantPermissions(pm, VENDING_PACKAGE, VENDING_PERMISSIONS);
    }

    private static void grantPermissions(PackageManager pm, String pkg, String[] perms) {
        for (String perm : perms) {
            grantPermission(pm, pkg, perm);
        }
    }

    private static void grantPermission(PackageManager pm, String pkg, String perm) {
        try {
            pm.grantRuntimePermission(pkg, perm, Process.myUserHandle());
            pm.updatePermissionFlags(perm, pkg, PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT,
                    PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT, Process.myUserHandle());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean needWhitelist(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        Set<Integer> uids =
                ConnectivitySettingsManager.getUidsAllowedOnRestrictedNetworks(
                        ctx);
        try {
            return !uids.contains(getUidForPkgName(pm, GMS_CORE_PACKAGE));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static List<Integer> getUidsForPkgNames(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        ArrayList<Integer> u = new ArrayList<>();
        for (String pkgName : MICROG_PACKAGES) {
            try {
                u.add(getUidForPkgName(pm, pkgName));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return u.stream().distinct().collect(Collectors.toList());
    }

    private static int getUidForPkgName(PackageManager pm, String pkgName)
            throws PackageManager.NameNotFoundException {
        return pm.getApplicationInfo(pkgName, 0).uid;
    }

    private static void addAllUids(Context ctx, List<Integer> uid) {
        Set<Integer> uids =
                ConnectivitySettingsManager.getUidsAllowedOnRestrictedNetworks(
                        ctx);
        uids.addAll(uid);
        ConnectivitySettingsManager.setUidsAllowedOnRestrictedNetworks(ctx,
                uids);
    }

    private boolean isMicroGApexPresent() {
        long apexVersion = getApexVersion(getContext(), MICROG_APEX);
        // if version is greater than 1, then non-stub microG apex is present.
        return apexVersion > 1L;
    }

    private static long getApexVersion(Context context, String apexName) {
        PackageManager packageManager = context.getPackageManager();

        List<PackageInfo> installedPackages =
                packageManager.getInstalledPackages(
                        PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));

        long apexVersion = -1L;
        for (PackageInfo pkg : installedPackages) {
            if (pkg.packageName.contains(apexName) && pkg.isApex) {
                apexVersion = pkg.getLongVersionCode();
            }
        }
        return apexVersion;
    }

}
