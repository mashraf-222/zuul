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

    public static String stripMaliciousHeaderChars(@Nullable String input) {
        if (input == null) {
            return null;
        }

        // Build a fast lookup table for malicious chars and scan input once,
        // avoiding repeated indexOf/replace calls and unnecessary string allocations.
        char[] bad = MALICIOUS_HEADER_CHARS;
        if (bad.length == 0) {
            return input;
        }

        int max = 0;
        for (char c : bad) {
            if (c > max) {
                max = c;
            }
        }

        boolean[] isBad = new boolean[max + 1];
        for (char c : bad) {
            isBad[c] = true;
        }

        StringBuilder sb = null;
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);
            if (ch <= max && isBad[ch]) {
                // first time we encounter a bad char, lazy-create a StringBuilder
                // and copy the prefix up to this point
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(input, 0, i);
                }
                // skip this malicious char
            } else if (sb != null) {
                // only append when we've already started building a cleaned string
                sb.append(ch);
            }
        }

        return sb == null ? input : sb.toString();
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
