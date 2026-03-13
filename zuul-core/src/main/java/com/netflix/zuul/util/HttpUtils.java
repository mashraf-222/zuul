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
package com.netflix.zuul.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.netflix.zuul.message.Headers;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpHeaderNames;
import com.netflix.zuul.message.http.HttpRequestInfo;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2StreamChannel;
import java.util.Locale;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Mike Smith
 * Date: 4/28/15
 * Time: 11:05 PM
 */
public class HttpUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
    private static final char[] MALICIOUS_HEADER_CHARS = {'\r', '\n'};

    /**
     * Get the IP address of client making the request.
     *
     * Uses the "x-forwarded-for" HTTP header if available, otherwise uses the remote
     * IP of requester.
     *
     * @param request <code>HttpRequestMessage</code>
     * @return <code>String</code> IP address
     */
    public static String getClientIP(HttpRequestInfo request) {
        String xForwardedFor = request.getHeaders().getFirst(HttpHeaderNames.X_FORWARDED_FOR);
        String clientIP;
        if (xForwardedFor == null) {
            clientIP = request.getClientIp();
        } else {
            clientIP = extractClientIpFromXForwardedFor(xForwardedFor);
        }
        return clientIP;
    }

    /**
     * Extract the client IP address from an x-forwarded-for header. Returns null if there is no x-forwarded-for header
     *
     * @param xForwardedFor a <code>String</code> value
     * @return a <code>String</code> value
     */
    public static String extractClientIpFromXForwardedFor(String xForwardedFor) {
        if (xForwardedFor == null) {
            return null;
        }
        xForwardedFor = xForwardedFor.trim();
        String tokenized[] = xForwardedFor.split(",", -1);
        if (tokenized.length == 0) {
            return null;
        } else {
            return tokenized[0].trim();
        }
    }

    @VisibleForTesting
    static boolean isCompressed(String contentEncoding) {
        return contentEncoding.contains(HttpHeaderValues.GZIP.toString())
                || contentEncoding.contains(HttpHeaderValues.DEFLATE.toString())
                || contentEncoding.contains(HttpHeaderValues.BR.toString())
                || contentEncoding.contains(HttpHeaderValues.COMPRESS.toString());
    }

    public static boolean isCompressed(Headers headers) {
        String ce = headers.getFirst(HttpHeaderNames.CONTENT_ENCODING);
        return ce != null && isCompressed(ce);
    }

    public static boolean acceptsGzip(Headers headers) {
        String ae = headers.getFirst(HttpHeaderNames.ACCEPT_ENCODING);
        return ae != null && ae.contains(HttpHeaderValues.GZIP.toString());
    }

    /**
         * Ensure decoded new lines are not propagated in headers, in order to prevent XSS
         *
         * @param input - decoded header string
         * @return - clean header string
         */
        // Efficient single-pass removal using a precomputed bitmap of malicious characters.
        public static String stripMaliciousHeaderChars(@Nullable String input) {
            if (input == null) {
                return null;
            }

            // Lazy precomputed map of malicious chars for fast lookup.
            // This avoids repeated O(n*m) scans and many intermediate String allocations.
            final boolean[] map = MALICIOUS_HEADER_CHAR_MAP;

            int len = input.length();
            // First quick scan to determine if any malicious char exists. If none, return original input.
            for (int i = 0; i < len; i++) {
                char c = input.charAt(i);
                if (c < map.length && map[c]) {
                    // Build result skipping malicious chars
                    StringBuilder sb = new StringBuilder(len);
                    for (int j = 0; j < len; j++) {
                        char cj = input.charAt(j);
                        if (!(cj < map.length && map[cj])) {
                            sb.append(cj);
                        }
                    }
                    return sb.toString();
                }
            }
            return input;
        }

        // Precompute a fast lookup table for malicious header characters.
        // Using a bitmap sized to Character.MAX_VALUE+1 (65536) keeps lookups O(1) and avoids
        // creating temporary Sets/Boxes on every call. Memory cost is small and allocated once.
        private static final boolean[] MALICIOUS_HEADER_CHAR_MAP = buildMaliciousCharMap();

        private static boolean[] buildMaliciousCharMap() {
            boolean[] map = new boolean[Character.MAX_VALUE + 1];
            for (char c : MALICIOUS_HEADER_CHARS) {
                map[c] = true;
            }
            return map;
        }

    public static boolean hasNonZeroContentLengthHeader(ZuulMessage msg) {
        Integer contentLengthVal = getContentLengthIfPresent(msg);
        return (contentLengthVal != null) && (contentLengthVal > 0);
    }

    public static Integer getContentLengthIfPresent(ZuulMessage msg) {
        String contentLengthValue =
                msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.CONTENT_LENGTH);
        if (!Strings.isNullOrEmpty(contentLengthValue)) {
            try {
                return Integer.valueOf(contentLengthValue);
            } catch (NumberFormatException e) {
                LOG.info("Invalid Content-Length header value on request. " + "value = {}", contentLengthValue, e);
            }
        }
        return null;
    }

    public static Integer getBodySizeIfKnown(ZuulMessage msg) {
        Integer bodySize = getContentLengthIfPresent(msg);
        if (bodySize != null) {
            return bodySize;
        }
        if (msg.hasCompleteBody()) {
            return msg.getBodyLength();
        }
        return null;
    }

    public static boolean hasChunkedTransferEncodingHeader(ZuulMessage msg) {
        boolean isChunked = false;
        String teValue = msg.getHeaders().getFirst(com.netflix.zuul.message.http.HttpHeaderNames.TRANSFER_ENCODING);
        if (!Strings.isNullOrEmpty(teValue)) {
            isChunked = teValue.toLowerCase(Locale.ROOT).equals("chunked");
        }
        return isChunked;
    }

    /**
     * If http/1 then will always want to just use ChannelHandlerContext.channel(), but for http/2
     * will want the parent channel (as the child channel is different for each h2 stream).
     */
    public static Channel getMainChannel(ChannelHandlerContext ctx) {
        return getMainChannel(ctx.channel());
    }

    public static Channel getMainChannel(Channel channel) {
        if (channel instanceof Http2StreamChannel) {
            return channel.parent();
        }
        return channel;
    }
}
