/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.netty.common.channel.config;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 6:17 PM
 */
public class ChannelConfigKey<T> {
    private final String key;
    private final T defaultValue;

    public ChannelConfigKey(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public ChannelConfigKey(String key) {
        this.key = key;
        this.defaultValue = null;
    }

    public String key() {
        return key;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Override
    public String toString() {
        // Use local references to avoid repeated field access and compute defaultValue's string once.
        String k = key;
        String dv = String.valueOf(defaultValue);
        // Estimate capacity to avoid StringBuilder growth: 39 is the combined length of the constant parts.
        int capacity = 39 + (k != null ? k.length() : 4) + dv.length();
        StringBuilder sb = new StringBuilder(capacity);
        sb.append("ChannelConfigKey{key='").append(k).append('\'').append(", defaultValue=").append(dv).append('}');
        return sb.toString();
    }
}
