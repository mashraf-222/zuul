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

package com.netflix.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: michaels@netflix.com
 * Date: 5/15/17
 * Time: 4:38 PM
 */
public class PatternListStringProperty extends DerivedStringProperty<List<Pattern>> {
    private static final Logger LOG = LoggerFactory.getLogger(PatternListStringProperty.class);

    public PatternListStringProperty(String name, String defaultValue) {
        super(name, defaultValue);
    }

    @Override
    protected List<Pattern> derive(String value) {
        ArrayList<Pattern> ptns = new ArrayList<>();
        if (value != null) {
            int len = value.length();
            // Estimate tokens as commas + 1 to avoid repeated resizing
            int commas = 0;
            for (int i = 0; i < len; i++) {
                if (value.charAt(i) == ',') {
                    commas++;
                }
            }
            ptns = new ArrayList<>(Math.max(1, commas + 1));

            // Cache commonly used strings to avoid repeated calls in the loop
            String nameStr = String.valueOf(this.getName());
            String valueStr = String.valueOf(this.getValue());
            String patternStr = String.valueOf(value);

            int start = 0;
            while (true) {
                int idx = value.indexOf(',', start);
                int end = (idx == -1) ? len : idx;

                // Trim using index arithmetic to avoid creating an extra String from trim()
                int ts = start;
                while (ts < end && value.charAt(ts) <= ' ') {
                    ts++;
                }
                int te = end;
                while (te > ts && value.charAt(te - 1) <= ' ') {
                    te--;
                }
                String token = value.substring(ts, te);
                try {
                    ptns.add(Pattern.compile(token));
                } catch (Exception e) {
                    LOG.error(
                            "Error parsing regex pattern list from property! name = {}, value = {}, pattern = {}",
                            nameStr,
                            valueStr,
                            patternStr);
                }

                if (idx == -1) {
                    break;
                }
                start = idx + 1;
            }
        }
        return ptns;
    }
}
