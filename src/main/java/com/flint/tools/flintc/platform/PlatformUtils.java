/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.flint.tools.flintc.platform;

import com.flint.tools.flintc.main.Arguments;
import com.flint.tools.flintc.platform.PlatformProvider.PlatformNotSupported;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/** Internal utilities to work with PlatformDescriptions.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class PlatformUtils {

    public static PlatformDescription lookupPlatformDescription(String platformString) {
        int separator = platformString.indexOf(":");
        String platformProviderName =
                separator != (-1) ? platformString.substring(0, separator) : platformString;
        String platformOptions =
                separator != (-1) ? platformString.substring(separator + 1) : "";
        Iterable<PlatformProvider> providers =
                ServiceLoader.load(PlatformProvider.class, Arguments.class.getClassLoader());

        return StreamSupport.stream(providers.spliterator(), false)
                            .filter(provider -> StreamSupport.stream(provider.getSupportedPlatformNames()
                                                                             .spliterator(),
                                                                     false)
                                                             .anyMatch(platformProviderName::equals))
                            .findFirst()
                            .flatMap(provider -> {
                                try {
                                    return Optional.of(provider.getPlatform(platformProviderName, platformOptions));
                                } catch (PlatformNotSupported pns) {
                                    return Optional.empty();
                                }
                            })
                            .orElse(null);
    }

}
